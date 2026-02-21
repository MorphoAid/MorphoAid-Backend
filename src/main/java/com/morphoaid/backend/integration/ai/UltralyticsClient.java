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

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UltralyticsClient.class);

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

    public String predict(byte[] imageBytes, String filename) {
        if (properties.getApiKey() == null || properties.getApiKey().trim().isEmpty()) {
            throw new UltralyticsException("Ultralytics API key is not configured.");
        }

        logger.info("Calling Ultralytics with:\nmodel={}\nimgsz={}\nconf={}\niou={}",
                properties.getModelUrl(), properties.getImgsz(), properties.getConf(), properties.getIou());

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
        if (properties.getModelUrl() != null)
            body.add("model", properties.getModelUrl());
        if (properties.getImgsz() != null)
            body.add("imgsz", properties.getImgsz());
        if (properties.getConf() != null)
            body.add("conf", properties.getConf());
        if (properties.getIou() != null)
            body.add("iou", properties.getIou());

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
