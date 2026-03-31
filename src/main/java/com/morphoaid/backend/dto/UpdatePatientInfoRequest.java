package com.morphoaid.backend.dto;

import lombok.Data;

@Data
public class UpdatePatientInfoRequest {
    private Long patientCode;
    private String patientMetadata;
}
