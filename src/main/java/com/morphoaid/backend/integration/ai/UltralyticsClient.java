package com.morphoaid.backend.integration.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Service
public class UltralyticsClient {

    private final RestTemplate restTemplate;
    private final UltralyticsProperties properties;

    @Autowired
    public UltralyticsClient(RestTemplateBuilder restTemplateBuilder, UltralyticsProperties properties) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .rootUri(properties.getBaseUrl())
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }

    public String predict(byte[] imageBytes, String filename, UltralyticsPredictRequest request) {
        if (properties.getApiKey() == null || properties.getApiKey().trim().isEmpty()) {
            throw new UltralyticsException("Ultralytics API key is not configured.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("x-api-key", properties.getApiKey());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "image.jpg";
            }
        };

        body.add("file", imageResource);
        if (request.model() != null)
            body.add("model", request.model());
        if (request.imgsz() != null)
            body.add("imgsz", request.imgsz());
        if (request.conf() != null)
            body.add("conf", request.conf());
        if (request.iou() != null)
            body.add("iou", request.iou());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/",
                    HttpMethod.POST,
                    requestEntity,
                    String.class);
            return response.getBody();
        } catch (RestClientException e) {
            throw new UltralyticsException("Failed to communicate with Ultralytics API: " + e.getMessage(), e);
        }
    }
}
