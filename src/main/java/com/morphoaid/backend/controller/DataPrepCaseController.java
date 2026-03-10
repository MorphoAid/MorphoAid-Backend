package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.CaseService;
import com.morphoaid.backend.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/dataprep/cases")
@PreAuthorize("hasAnyRole('DATA_PREP', 'ADMIN')")
public class DataPrepCaseController {

    private static final Logger logger = LoggerFactory.getLogger(DataPrepCaseController.class);

    private final CaseService caseService;
    private final StorageService storageService;
    private final UserRepository userRepository;

    @Autowired
    public DataPrepCaseController(CaseService caseService, StorageService storageService,
            UserRepository userRepository) {
        this.caseService = caseService;
        this.storageService = storageService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> uploadDataPrepCase(
            @RequestParam("image") MultipartFile image,
            java.security.Principal principal) {

        // Validate image
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().body("Image file is required.");
        }

        // Validate file extensions (JPG/PNG)
        String originalFilename = image.getOriginalFilename();
        if (originalFilename != null) {
            String lowerCaseName = originalFilename.toLowerCase();
            if (!lowerCaseName.endsWith(".png") && !lowerCaseName.endsWith(".jpg")
                    && !lowerCaseName.endsWith(".jpeg")) {
                return ResponseEntity.badRequest().body("Invalid file type. Only JPG and PNG are allowed.");
            }
        }

        String contentType = image.getContentType();
        if (contentType != null && !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body("Invalid content type. Must be an image.");
        }

        try {
            // Save real image path temporarily for analyze demonstration
            java.io.File destDir = new java.io.File(System.getProperty("user.dir"), "debug");
            if (!destDir.exists())
                destDir.mkdirs();

            String safeFilename = UUID.randomUUID() + "-" + image.getOriginalFilename();
            java.io.File destFile = new java.io.File(destDir, safeFilename);

            // Read bytes to avoid irreversibly consuming the file stream beforehand
            byte[] fileBytes = image.getBytes();
            java.nio.file.Files.write(destFile.toPath(), fileBytes);

            String realImagePath = destFile.getAbsolutePath();

            User uploader = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Uploader not found"));

            // Call caseService.createCase (returns DTO)
            // Passing null for PII fields ensures true anonymization
            CaseResponse newCase = caseService.createCase(null, realImagePath, null, null,
                    uploader.getId());

            // Upload to S3 to create the CaseImage record for AI analysis requirement
            storageService.uploadCaseImage(newCase.getId(), image, uploader);

            // Fetch the updated CaseResponse with image details
            CaseResponse updatedCase = caseService.getCaseOrThrow(newCase.getId());

            logger.info("DataPrep created new case with ID: {}", updatedCase.getId());

            // Trigger AI analysis server-side so analysis_status transitions properly
            try {
                caseService.analyzeCase(updatedCase.getId());
                // Re-fetch to reflect the updated analysis_status
                updatedCase = caseService.getCaseOrThrow(updatedCase.getId());
            } catch (Exception e) {
                logger.warn("AI analysis for DataPrep case {} failed: {}", updatedCase.getId(), e.getMessage());
                // Not fatal — the frontend can retry via the Retry button
                updatedCase = caseService.getCaseOrThrow(updatedCase.getId());
            }

            // Return latest DTO
            return ResponseEntity.ok(updatedCase);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error creating case via DataPrep: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(java.util.Map.of(
                    "message", "Error creating case: " + e.getMessage(),
                    "type", e.getClass().getSimpleName()));
        }
    }
}
