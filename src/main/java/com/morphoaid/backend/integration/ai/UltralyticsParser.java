package com.morphoaid.backend.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UltralyticsParser {

    private final ObjectMapper objectMapper;

    // Ultralytics usually returns an array of image results
    public UltralyticsParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<UltralyticsDetection> parseTopDetection(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);

            // Expected format: array of objects containing `boxes` or `probs` arrays...
            // Standard predict API often returns array of images
            if (root.isArray() && !root.isEmpty()) {
                JsonNode firstImageResult = root.get(0);
                JsonNode namesMap = firstImageResult.path("names");
                JsonNode boxes = firstImageResult.path("boxes");

                if (boxes.isArray() && !boxes.isEmpty()) {
                    // Find box with highest confidence
                    JsonNode topBox = null;
                    double maxConf = -1.0;

                    for (JsonNode box : boxes) {
                        double conf = box.path("conf").asDouble(0.0);
                        if (conf > maxConf) {
                            maxConf = conf;
                            topBox = box;
                        }
                    }

                    if (topBox != null) {
                        int classId = topBox.path("class").asInt(0);
                        return Optional.of(mapDetection(classId, maxConf, rawJson));
                    }
                }

                // If the model is classification instead of detection
                JsonNode probs = firstImageResult.path("probs");
                if (probs.isObject() && probs.has("top1") && probs.has("top1conf")) {
                    int classId = probs.get("top1").asInt();
                    double conf = probs.get("top1conf").asDouble();
                    return Optional.of(mapDetection(classId, conf, rawJson));
                }
            }
        } catch (Exception e) {
            throw new UltralyticsException("Failed to parse AI JSON response", e);
        }

        return Optional.empty(); // No detections
    }

    private UltralyticsDetection mapDetection(int classId, double confidence, String rawJson) {
        Boolean drugExposure = false;
        String drugType = null;
        String parasiteStage = null;

        /**
         * Mapping Rules:
         * class 0 -> drugExposure=true, drugType="A"
         * class 1 -> drugExposure=true, drugType="B"
         * class 2 -> drugExposure=false, parasiteStage="RING"
         * class 3 -> drugExposure=false, parasiteStage="SCHIZ"
         * class 4 -> drugExposure=false, parasiteStage="TROPH"
         */
        switch (classId) {
            case 0:
                drugExposure = true;
                drugType = "A";
                break;
            case 1:
                drugExposure = true;
                drugType = "B";
                break;
            case 2:
                drugExposure = false;
                parasiteStage = "RING";
                break;
            case 3:
                drugExposure = false;
                parasiteStage = "SCHIZ";
                break;
            case 4:
                drugExposure = false;
                parasiteStage = "TROPH";
                break;
            default:
                // Unmapped class
                break;
        }

        return new UltralyticsDetection(
                classId,
                confidence,
                drugExposure,
                drugType,
                parasiteStage,
                rawJson);
    }
}
