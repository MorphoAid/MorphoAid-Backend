package com.morphoaid.backend.integration.ai;

public record UltralyticsDetection(
        Integer topClassId,
        Double confidence,
        Boolean drugExposure,
        String drugType,
        String parasiteStage,
        String rawResponseJson) {
}
