package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.AuthResponse;
import com.morphoaid.backend.dto.LoginRequest;
import com.morphoaid.backend.dto.RegisterDataUseRequest;
import com.morphoaid.backend.dto.RegisterDataPrepRequest;
import com.morphoaid.backend.dto.UserSummary;
import jakarta.validation.Valid;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.InvitationTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InvitationTokenService tokenService;
    private final com.morphoaid.backend.security.JwtService jwtService;
    private final com.morphoaid.backend.service.ActivityService activityService;

    @Autowired
    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
            InvitationTokenService tokenService, com.morphoaid.backend.security.JwtService jwtService,
            com.morphoaid.backend.service.ActivityService activityService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jwtService = jwtService;
        this.activityService = activityService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterDataUseRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            request.setUsername("u" + java.util.UUID.randomUUID().toString().substring(0, 8));
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return buildValidationError("confirmPassword", "Passwords do not match");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return buildValidationError("email", "Email already in use");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return buildValidationError("username", "Username already in use");
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.DATA_USE)
                .approved(false)  // New DATA_USE users need admin approval
                .build();

        user = userRepository.save(user);

        // Log Activity
        activityService.log(
            user.getEmail(), 
            user.getRole(), 
            "User Registration", 
            "Pending Approval", 
            "Success"
        );

        return ResponseEntity.status(201).body(buildDummyResponse(user));
    }

    @PostMapping("/register/dataprep")
    public ResponseEntity<?> registerDataPrep(@RequestBody @Valid RegisterDataPrepRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            request.setUsername("u" + java.util.UUID.randomUUID().toString().substring(0, 8));
        }

        try {
            tokenService.validateToken(request.getInvitationToken());
        } catch (IllegalArgumentException e) {
            return buildValidationError("invitationToken", "Invalid or missing token");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return buildValidationError("confirmPassword", "Passwords do not match");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return buildValidationError("email", "Email already in use");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return buildValidationError("username", "Username already in use");
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.DATA_PREP)
                .build();

        user = userRepository.save(user);
        tokenService.markUsed(request.getInvitationToken(), user.getId());

        // Log Activity
        activityService.log(
            user.getEmail(), 
            user.getRole(), 
            "User Registration", 
            "Data Prep (Token Used)", 
            "Success"
        );

        return ResponseEntity.status(201).body(buildDummyResponse(user));
    }

    private ResponseEntity<java.util.Map<String, Object>> buildValidationError(String field, String message) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        response.put("status", 400);
        response.put("errors", java.util.Map.of(field, message));
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthController.class);

        if (user == null) {
            logger.warn("Login failed: User not found for email {}", request.getEmail());
            return ResponseEntity.status(401).build();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Login failed: Password mismatch for email {}", request.getEmail());
            return ResponseEntity.status(401).build();
        }

        boolean remember = Boolean.TRUE.equals(request.getRememberMe());
        logger.info("Login successful for email {}, rememberMe={}", request.getEmail(), remember);

        return ResponseEntity.ok(buildDummyResponse(user));
    }

    private AuthResponse buildDummyResponse(User user) {
        UserSummary summary = UserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(user.getRole())
                .profilePictureUrl(buildProfilePictureUrl(user.getProfilePictureUrl(), user.getId()))
                .approved(user.isApproved())
                .build();

        return AuthResponse.builder()
                .accessToken(jwtService.generateToken(user))
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime() / 1000)
                .user(summary)
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummary> getCurrentUser(java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(principal.getName())
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        UserSummary summary = UserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(user.getRole())
                .profilePictureUrl(buildProfilePictureUrl(user.getProfilePictureUrl(), user.getId()))
                .approved(user.isApproved())
                .build();

        return ResponseEntity.ok(summary);
    }
    
    private String buildProfilePictureUrl(String dbValue, Long userId) {
        if (dbValue == null) return null;
        if (dbValue.contains("via.placeholder.com")) return dbValue;
        try {
            return org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/users/{id}/profile-picture")
                .buildAndExpand(userId)
                .toUriString();
        } catch(Exception e) {
            return dbValue;
        }
    }
}
