package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.CreateInvitationRequest;
import com.morphoaid.backend.dto.InvitationResponse;
import com.morphoaid.backend.entity.InvitationToken;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.repository.InvitationTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/invitations")
public class AdminInvitationController {

        private final InvitationTokenRepository tokenRepository;

        @Autowired
        public AdminInvitationController(InvitationTokenRepository tokenRepository) {
                this.tokenRepository = tokenRepository;
        }

        @PostMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<InvitationResponse> createInvitation(@RequestBody CreateInvitationRequest request) {
                int days = (request.getExpiresInDays() != null && request.getExpiresInDays() > 0)
                                ? request.getExpiresInDays()
                                : 7;

                InvitationToken token = InvitationToken.builder()
                                .token(UUID.randomUUID().toString())
                                .role(Role.DATA_PREP)
                                .expiresAt(LocalDateTime.now().plusDays(days))
                                .build();

                token = tokenRepository.save(token);

                return ResponseEntity.ok(InvitationResponse.builder()
                                .code(token.getToken())
                                .createdAt(token.getCreatedAt())
                                .expiresAt(token.getExpiresAt())
                                .usedAt(token.getUsedAt())
                                .usedByUserId(token.getUsedByUserId())
                                .build());
        }

        @GetMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<List<InvitationResponse>> listInvitations() {
                List<InvitationToken> tokens = tokenRepository.findAll();

                List<InvitationResponse> response = tokens.stream()
                                .map(token -> InvitationResponse.builder()
                                                .code(token.getToken()) // Alternatively, mask this if explicitly
                                                                        // required
                                                .createdAt(token.getCreatedAt())
                                                .expiresAt(token.getExpiresAt())
                                                .usedAt(token.getUsedAt())
                                                .usedByUserId(token.getUsedByUserId())
                                                .build())
                                .collect(Collectors.toList());

                return ResponseEntity.ok(response);
        }
}
