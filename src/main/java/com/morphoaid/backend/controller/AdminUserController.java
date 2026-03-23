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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminUserController {

    private final UserRepository userRepository;

    @Autowired
    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping(value = "/users", produces = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminUserResponse>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<AdminUserResponse> response = users.stream()
                .map(user -> AdminUserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt())
                        .build())
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

        AdminUserResponse response = AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();

        return ResponseEntity.ok(response);
    }

    /** GET /admin/users/pending — list unapproved users */
    @GetMapping(value = "/users/pending", produces = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminUserResponse>> getPendingUsers() {
        List<User> pending = userRepository.findByApproved(false);
        List<AdminUserResponse> response = pending.stream()
                .map(user -> AdminUserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt())
                        .build())
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
        return ResponseEntity.ok(Map.of("message", "User rejected and removed", "id", id));
    }
}
