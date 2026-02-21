package com.morphoaid.backend.service;

import com.morphoaid.backend.entity.InvitationToken;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.repository.InvitationTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class InvitationTokenService {

    private final InvitationTokenRepository tokenRepository;

    @Autowired
    public InvitationTokenService(InvitationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public InvitationToken validateToken(String tokenStr) {
        InvitationToken token = tokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token"));

        if (token.getUsedAt() != null || token.getUsedByUserId() != null) {
            throw new IllegalArgumentException("Invitation token has already been used");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invitation token has expired");
        }

        if (token.getRole() != Role.DATA_PREP) {
            throw new IllegalArgumentException("Invitation token is not valid for DATA_PREP role");
        }

        return token;
    }

    @Transactional
    public void markUsed(String tokenStr, Long userId) {
        InvitationToken token = validateToken(tokenStr);
        token.setUsedAt(LocalDateTime.now());
        token.setUsedByUserId(userId);
        tokenRepository.save(token);
    }
}
