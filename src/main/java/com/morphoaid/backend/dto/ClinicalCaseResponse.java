package com.morphoaid.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ClinicalCaseResponse {
    private Long id;
    private String status;
    private String analysisStatus;
    private Long imageId;
    private String provinceCode;
    private String provinceName;
    private Boolean consent;
    private String patientMetadata;
    private LocalDateTime createdAt;
}
