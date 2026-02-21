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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminUserController {

    private final UserRepository userRepository;

    @Autowired
    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
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
}
