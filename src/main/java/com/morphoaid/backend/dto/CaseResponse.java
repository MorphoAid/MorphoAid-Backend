package com.morphoaid.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CaseResponse {
    private Long id;
    private Long patientCode;
    private String technicianId;
    private String location;
    private String status;
    private String analysisStatus;
    private String imagePath; // TODO: Expose a single CaseImage object when frontend allows
    private Long imageId;
    private String imageFilename;
    private LocalDateTime createdAt;
    private Long uploadedById;
}
