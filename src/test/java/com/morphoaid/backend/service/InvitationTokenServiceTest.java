package com.morphoaid.backend.service;

import com.morphoaid.backend.entity.InvitationToken;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.repository.InvitationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationTokenServiceTest {

    @Mock
    private InvitationTokenRepository tokenRepository;

    @InjectMocks
    private InvitationTokenService invitationTokenService;

    private InvitationToken validToken;

    @BeforeEach
    void setUp() {
        validToken = InvitationToken.builder()
                .id(1L)
                .token("VALID_TOKEN_123")
                .role(Role.DATA_PREP)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    // UTC-05-TC-01 Validate a valid invitation token successfully
    @Test
    void validateToken_Success() {
        // Arrange
        when(tokenRepository.findByToken("VALID_TOKEN_123")).thenReturn(Optional.of(validToken));

        // Act
        InvitationToken result = invitationTokenService.validateToken("VALID_TOKEN_123");

        // Assert
        assertNotNull(result);
        assertEquals("VALID_TOKEN_123", result.getToken());
        assertEquals(Role.DATA_PREP, result.getRole());
        verify(tokenRepository, times(1)).findByToken("VALID_TOKEN_123");
    }

    // UTC-05-TC-02 Reject validation when invitation token is missing
    @Test
    void validateToken_MissingToken_ThrowsException() {
        // Arrange
        when(tokenRepository.findByToken("INVALID_TOKEN")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            invitationTokenService.validateToken("INVALID_TOKEN");
        });

        assertEquals("Invalid invitation token", exception.getMessage());
        verify(tokenRepository, times(1)).findByToken("INVALID_TOKEN");
    }

    // UTC-05-TC-03 Reject validation when invitation token is invalid (Expired)
    @Test
    void validateToken_ExpiredToken_ThrowsException() {
        // Arrange
        InvitationToken expiredToken = InvitationToken.builder()
                .token("EXPIRED_TOKEN")
                .role(Role.DATA_PREP)
                .expiresAt(LocalDateTime.now().minusDays(1)) // Expired yesterday
                .build();

        when(tokenRepository.findByToken("EXPIRED_TOKEN")).thenReturn(Optional.of(expiredToken));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            invitationTokenService.validateToken("EXPIRED_TOKEN");
        });

        assertEquals("Invitation token has expired", exception.getMessage());
    }

    // UTC-05-TC-03 Reject validation when invitation token is invalid (Wrong Role)
    @Test
    void validateToken_WrongRole_ThrowsException() {
        // Arrange
        InvitationToken wrongRoleToken = InvitationToken.builder()
                .token("WRONG_ROLE_TOKEN")
                .role(Role.DATA_USE) // Invalid role for token
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(tokenRepository.findByToken("WRONG_ROLE_TOKEN")).thenReturn(Optional.of(wrongRoleToken));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            invitationTokenService.validateToken("WRONG_ROLE_TOKEN");
        });

        assertEquals("Invitation token is not valid for DATA_PREP role", exception.getMessage());
    }

    // UTC-05-TC-04 Reject validation when invitation token has already been used
    @Test
    void validateToken_AlreadyUsedToken_ThrowsException() {
        // Arrange
        InvitationToken usedToken = InvitationToken.builder()
                .token("USED_TOKEN")
                .role(Role.DATA_PREP)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .usedAt(LocalDateTime.now().minusHours(1))
                .usedByUserId(99L)
                .build();

        when(tokenRepository.findByToken("USED_TOKEN")).thenReturn(Optional.of(usedToken));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            invitationTokenService.validateToken("USED_TOKEN");
        });

        assertEquals("Invitation token has already been used", exception.getMessage());
    }

    // UTC-06-TC-01 Mark invitation token as used successfully after registration
    // UTC-06-TC-02 Associate invitation token with registered user correctly
    @Test
    void markUsed_Success() {
        // Arrange
        Long userId = 42L;
        when(tokenRepository.findByToken("VALID_TOKEN_123")).thenReturn(Optional.of(validToken));
        when(tokenRepository.save(any(InvitationToken.class))).thenReturn(validToken);

        // Act
        invitationTokenService.markUsed("VALID_TOKEN_123", userId);

        // Assert
        assertNotNull(validToken.getUsedAt(), "Token usedAt timestamp should be set");
        assertEquals(userId, validToken.getUsedByUserId(), "Token should be associated with the given user ID");
        verify(tokenRepository, times(1)).findByToken("VALID_TOKEN_123");
        verify(tokenRepository, times(1)).save(validToken);
    }
}
