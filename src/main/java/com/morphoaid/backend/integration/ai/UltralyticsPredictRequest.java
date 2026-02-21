package com.morphoaid.backend.integration.ai;

public record UltralyticsPredictRequest(
        String model,
        Integer imgsz,
        Double conf,
        Double iou) {
}
