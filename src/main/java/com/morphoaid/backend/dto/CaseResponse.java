package com.morphoaid.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CaseResponse {
    private Long id;
    private String patientCode;
    private String technicianId;
    private String location;
    private String status;
    private String imagePath;
    private LocalDateTime createdAt;
    private Long uploadedById;
}
