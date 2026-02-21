package com.morphoaid.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {

    private String token;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private Long usedByUserId;
}
