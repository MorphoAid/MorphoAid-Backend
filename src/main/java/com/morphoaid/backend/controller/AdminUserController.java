package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.AdminUserResponse;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.morphoaid.backend.service.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import java.io.InputStream;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminUserController {

    private final UserRepository userRepository;
    private final com.morphoaid.backend.service.ActivityService activityService;
    private final StorageService storageService;

    @Autowired
    public AdminUserController(UserRepository userRepository, 
                               com.morphoaid.backend.service.ActivityService activityService,
                               StorageService storageService) {
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.storageService = storageService;
    }

    @GetMapping(value = "/users", produces = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminUserResponse>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<AdminUserResponse> response = users.stream()
                .map(this::mapToResponse)

                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserResponse> updateUserRole(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.morphoaid.backend.dto.UpdateUserRequest request,
            java.security.Principal principal) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new com.morphoaid.backend.exception.NotFoundException("User not found"));

        // Optional: Prevent self-demotion
        if (user.getEmail().equals(principal.getName())
                && request.getRole() != com.morphoaid.backend.entity.Role.ADMIN) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == com.morphoaid.backend.entity.Role.ADMIN)
                    .count();
            if (adminCount <= 1) {
                return ResponseEntity.badRequest().build(); // Cannot remove the last admin
            }
        }

        user.setRole(request.getRole());
        userRepository.save(user);

        // Log Activity
        activityService.log(
            principal.getName(), 
            com.morphoaid.backend.entity.Role.ADMIN, 
            "Role Update", 
            "User " + user.getEmail() + " -> " + request.getRole(), 
            "Success"
        );

        AdminUserResponse response = mapToResponse(user);


        return ResponseEntity.ok(response);
    }

    /** GET /admin/users/pending — list unapproved users */
    @GetMapping(value = "/users/pending", produces = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminUserResponse>> getPendingUsers() {
        List<User> pending = userRepository.findByApproved(false);
        List<AdminUserResponse> response = pending.stream()
                .map(this::mapToResponse)

                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /** PATCH /admin/users/{id}/approve */
    @PatchMapping("/users/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setApproved(true);
                    userRepository.save(user);
                    
                    // Log Activity
                    activityService.log(
                        "admin", // Principal could be used if available in method sig
                        com.morphoaid.backend.entity.Role.ADMIN, 
                        "User Approval", 
                        "User " + user.getEmail(), 
                        "Success"
                    );
                    
                    return ResponseEntity.ok(Map.of("message", "User approved", "id", id));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /admin/users/{id}/reject */
    @DeleteMapping("/users/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();
        userRepository.deleteById(id);
        
        // Log Activity
        activityService.log(
            "admin", 
            com.morphoaid.backend.entity.Role.ADMIN, 
            "User Deletion/Reject", 
            "User ID #" + id, 
            "Success"
        );
        
        return ResponseEntity.ok(Map.of("message", "User rejected and removed", "id", id));
    }

    @GetMapping("/users/{id}/verification-document")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InputStreamResource> getVerificationDocument(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new com.morphoaid.backend.exception.NotFoundException("User not found"));

        if (user.getVerificationDocumentUrl() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            InputStream is = storageService.downloadFile(user.getVerificationDocumentUrl());
            
            // Try to guess content type from filename or default to octet-stream
            String contentType = "application/octet-stream";
            String filename = user.getVerificationDocumentUrl();
            if (filename.toLowerCase().endsWith(".pdf")) contentType = "application/pdf";
            else if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) contentType = "image/jpeg";
            else if (filename.toLowerCase().endsWith(".png")) contentType = "image/png";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new InputStreamResource(is));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    private AdminUserResponse mapToResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .organization(user.getOrganization())
                .title(user.getTitle())
                .licenseNumber(user.getLicenseNumber())
                .hospital(user.getHospital())
                .verificationDocumentUrl(buildVerificationDocumentUrl(user.getVerificationDocumentUrl(), user.getId()))
                .build();
    }

    private String buildVerificationDocumentUrl(String dbValue, Long userId) {
        if (dbValue == null) return null;
        if (dbValue.startsWith("http")) return dbValue;
        try {
            return org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/admin/users/{id}/verification-document")
                .buildAndExpand(userId)
                .toUriString();
        } catch(Exception e) {
            return dbValue;
        }
    }
}
