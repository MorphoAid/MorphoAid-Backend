package com.morphoaid.backend.dto;

import java.time.LocalDateTime;

/**
 * Anonymized DTO for Lab review endpoints.
 * ONLY whitelisted, non-PII fields are included.
 * Fields intentionally EXCLUDED: patientCode, technicianId, uploadedBy (User),
 * objectKey, bucket, checksum, email, fullName, firstName, lastName.
 */
public record LabReviewDto(
        Long caseId,
        String caseStatus, // PENDING | ANALYZED | REVIEWED
        String source, // "CLINICAL" (DATA_USE uploader) | "LAB_UPLOAD" (DATA_PREP uploader)
        LocalDateTime createdAt, // case creation timestamp
        Long imageId, // first image id — use GET /cases/{caseId}/images/{imageId}
        String parasiteStage, // "RING" | "SCHIZ" | "TROPH" | null
        Boolean drugExposure, // true if drug-resistant class
        String drugType, // "A" | "B" | null
        Integer topClassId, // raw model class 0-4
        Double confidence // top detection confidence [0..1]
) {
}
