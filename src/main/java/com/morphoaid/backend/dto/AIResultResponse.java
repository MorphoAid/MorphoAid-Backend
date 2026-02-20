package com.morphoaid.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AIResultResponse {
    private Long id;
    private Long caseId;
    private String parasiteStage;
    private Boolean drugExposure;
    private Double confidence;
    private String rawResponseJson;
    private LocalDateTime createdAt;
}
