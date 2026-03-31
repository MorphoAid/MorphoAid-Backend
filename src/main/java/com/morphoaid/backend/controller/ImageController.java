package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.service.CaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/cases")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final CaseService caseService;

    @Autowired
    public ImageController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<byte[]> getCaseImage(@PathVariable Long id) {
        try {
            CaseResponse caseData = caseService.getCaseOrThrow(id);
            String imagePath = caseData.getImagePath();

            if (imagePath == null || imagePath.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Path path = Paths.get(imagePath);
            if (!Files.exists(path)) {
                logger.warn("Image file not found on disk: {}", imagePath);
                return ResponseEntity.notFound().build();
            }

            byte[] imageBytes = Files.readAllBytes(path);

            // Determine content type from file extension
            String lowerPath = imagePath.toLowerCase();
            MediaType mediaType;
            if (lowerPath.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentLength(imageBytes.length);

            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
        } catch (com.morphoaid.backend.exception.NotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            logger.error("Failed to read image file for case {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
