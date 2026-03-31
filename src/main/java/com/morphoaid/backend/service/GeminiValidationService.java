package com.morphoaid.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiValidationService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiValidationService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiValidationService(org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
    }

    public record ValidationResult(boolean valid, String reason) {}

    public ValidationResult validateImage(byte[] imageBytes, String mimeType) {
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("Gemini API Key is missing. Skipping validation.");
            return new ValidationResult(true, "Skipped");
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mime_type", mimeType != null ? mimeType : "image/jpeg");
            inlineData.put("data", base64Image);

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("inline_data", inlineData);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", "Check if this image is a blood cell or a microscope image. Reply ONLY in JSON format: { \"valid\": true/false, \"reason\": \"Explanation in English of why it is valid, or why it is invalid and what the image actually is if invalid.\" }");

            Map<String, Object> contentPart = new HashMap<>();
            contentPart.put("parts", List.of(textPart, imagePart));

            Map<String, Object> requestBodyMap = new HashMap<>();
            requestBodyMap.put("contents", List.of(contentPart));

            // Optional: force JSON generation response if supported
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("response_mime_type", "application/json");
            requestBodyMap.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBodyMap, headers);

            String requestUrl = apiUrl + "?key=" + apiKey;
            ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                JsonNode candidates = rootNode.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode parts = candidates.get(0).path("content").path("parts");
                    if (parts.isArray() && !parts.isEmpty()) {
                        String textResponse = parts.get(0).path("text").asText();
                        // Parse the JSON text returned by Gemini
                        // Remove markdown formatting if Gemini included it e.g., ```json ... ```
                        textResponse = textResponse.replaceAll("```json", "").replaceAll("```", "").trim();

                        JsonNode jsonResponse = objectMapper.readTree(textResponse);
                        boolean isValid = jsonResponse.path("valid").asBoolean(false);
                        String reason = jsonResponse.path("reason").asText("Unknown reason");

                        logger.info("Gemini image validation completed. Result: {}, Reason: {}", isValid, reason);
                        return new ValidationResult(isValid, reason);
                    }
                }
            }
            logger.error("Failed to parse Gemini response: {}", response.getBody());
            // If it fails to parse, assume valid to not block users due to AI API errors
            return new ValidationResult(true, "Validation request failed or parsing error.");

        } catch (Exception e) {
            logger.error("Exception during Gemini Image Validation: {}", e.getMessage(), e);
            // Fallback to allow upload if Gemini fails entirely
            return new ValidationResult(true, "Validation error occurred. Allowed for now.");
        }
    }
}
