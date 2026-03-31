package com.morphoaid.backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDataUseRequest {
    private String username;

    @NotBlank(message = "First name is required")
    @Pattern(regexp = "^[A-Za-z\\u0E00-\\u0E7F\\s]+$", message = "First name must contain only alphabetic characters and spaces")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Pattern(regexp = "^[A-Za-z\\u0E00-\\u0E7F\\s]+$", message = "Last name must contain only alphabetic characters and spaces")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    @AssertTrue(message = "You must agree to the terms")
    private boolean agreeTerms;

    // Phase 2: Medical Identity Verification
    private String title;
    private String licenseNumber;
    private String hospital;
    private org.springframework.web.multipart.MultipartFile verificationDocument;
}
