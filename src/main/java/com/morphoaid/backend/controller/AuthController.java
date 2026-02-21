package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.AuthResponse;
import com.morphoaid.backend.dto.DataPrepRegisterRequest;
import com.morphoaid.backend.dto.LoginRequest;
import com.morphoaid.backend.dto.RegisterRequest;
import com.morphoaid.backend.dto.UserSummary;
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

    @Autowired
    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
            InvitationTokenService tokenService, com.morphoaid.backend.security.JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().build();
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.DATA_USE)
                .build();

        user = userRepository.save(user);

        return ResponseEntity.ok(buildDummyResponse(user));
    }

    @PostMapping("/register/dataprep")
    public ResponseEntity<AuthResponse> registerDataPrep(@RequestBody DataPrepRegisterRequest request) {
        // Validation of token implicitly throws exception if invalid
        tokenService.validateToken(request.getInvitationToken());

        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().build();
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.DATA_PREP)
                .build();

        user = userRepository.save(user);
        tokenService.markUsed(request.getInvitationToken(), user.getId());

        return ResponseEntity.ok(buildDummyResponse(user));
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

        logger.info("Login successful for email {}", request.getEmail());

        return ResponseEntity.ok(buildDummyResponse(user));
    }

    private AuthResponse buildDummyResponse(User user) {
        UserSummary summary = UserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
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
                .role(user.getRole())
                .build();

        return ResponseEntity.ok(summary);
    }
}
