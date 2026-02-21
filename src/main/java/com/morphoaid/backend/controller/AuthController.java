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

    @Autowired
    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
            InvitationTokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
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

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(buildDummyResponse(user));
    }

    private AuthResponse buildDummyResponse(User user) {
        UserSummary summary = UserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .build();

        return AuthResponse.builder()
                .accessToken("dummy-jwt-token-replace-in-step4")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(summary)
                .build();
    }
}
