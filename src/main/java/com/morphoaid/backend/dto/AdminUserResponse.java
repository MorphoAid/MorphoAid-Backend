package com.morphoaid.backend.dto;

import com.morphoaid.backend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private Long id;
    private String email;
    private Role role;
    private LocalDateTime createdAt;
    private String firstName;
    private String lastName;
    private String fullName;
    private String profilePictureUrl;
    private String organization;
    private String title;
    private String licenseNumber;
    private String hospital;
    private String verificationDocumentUrl;
}
