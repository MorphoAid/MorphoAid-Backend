package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.CaseService;
import com.morphoaid.backend.service.GeminiValidationService;
import com.morphoaid.backend.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cases")
public class CaseController {

    private static final Logger logger = LoggerFactory.getLogger(CaseController.class);

    private final CaseService caseService;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final GeminiValidationService geminiValidationService;

    @Autowired
    public CaseController(CaseService caseService, StorageService storageService, UserRepository userRepository, GeminiValidationService geminiValidationService) {
        this.caseService = caseService;
        this.storageService = storageService;
        this.userRepository = userRepository;
        this.geminiValidationService = geminiValidationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<?> uploadCase(
            @RequestParam("patientCode") Long patientCode,
            @RequestParam("technicianId") String technicianId,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam("image") MultipartFile image,
            java.security.Principal principal) {

        // Validate image
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validate file extensions for PNG, JPG, JPEG
        String originalFilename = image.getOriginalFilename();
        if (originalFilename != null) {
            String lowerCaseName = originalFilename.toLowerCase();
            if (!lowerCaseName.endsWith(".png") && !lowerCaseName.endsWith(".jpg")
                    && !lowerCaseName.endsWith(".jpeg")) {
                return ResponseEntity.badRequest().build(); // or throw custom exception
            }
        }

        String contentType = image.getContentType();
        if (contentType != null && !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            // Validate Image via Gemini API
            GeminiValidationService.ValidationResult validationResult = geminiValidationService.validateImage(image.getBytes(), image.getContentType());
            if (!validationResult.valid()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("message", validationResult.reason(), "type", "ImageValidationFailed"));
            }

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

            // Call caseService.createCase (now returns DTO)
            CaseResponse newCase = caseService.createCase(patientCode, realImagePath, technicianId, location,
                    uploader.getId());

            // Upload to S3 to create the CaseImage record for AI analysis requirement
            storageService.uploadCaseImage(newCase.getId(), image, uploader);

            // Fetch the updated CaseResponse with image details
            CaseResponse updatedCase = caseService.getCaseOrThrow(newCase.getId());

            logger.info("Created new case with ID: {}", updatedCase.getId());

            // Return latest DTO
            return ResponseEntity.ok(updatedCase);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating case: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(java.util.Map.of(
                    "message", "Error creating case: " + e.getMessage(),
                    "type", e.getClass().getSimpleName()));
        }
    }

    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    @PostMapping("/{caseId}/images")
    public ResponseEntity<?> uploadCaseImage(
            @PathVariable Long caseId,
            @RequestParam("image") MultipartFile image,
            java.security.Principal principal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.info("AUTH name={}, authorities={}", auth != null ? auth.getName() : null,
                auth != null ? auth.getAuthorities() : null);
        System.out.println("====== SECURITY DEBUG ======");
        System.out.println("AUTH = " + auth);
        System.out.println("NAME = " + (auth != null ? auth.getName() : null));
        System.out.println("AUTHORITIES = " + (auth != null ? auth.getAuthorities() : null));
        System.out.println("============================");

        try {
            // Validate Image via Gemini API
            GeminiValidationService.ValidationResult validationResult = geminiValidationService.validateImage(image.getBytes(), image.getContentType());
            if (!validationResult.valid()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("message", validationResult.reason(), "type", "ImageValidationFailed"));
            }

            caseService.verifyCaseAccess(caseId, principal.getName());

            User uploader = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Uploader not found"));

            storageService.uploadCaseImage(caseId, image, uploader);

            // Return the updated CaseResponse instead of just the CaseImage
            // This ensures the frontend gets the imageId and imageFilename immediately
            CaseResponse updatedCase = caseService.getCaseOrThrow(caseId);
            return ResponseEntity.ok().body(updatedCase);

        } catch (org.springframework.security.access.AccessDeniedException e) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error uploading image for case " + caseId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    @GetMapping("/{caseId}/images/{imageId}/content")
    public ResponseEntity<InputStreamResource> downloadImageContent(
            @PathVariable Long caseId,
            @PathVariable Long imageId,
            java.security.Principal principal) {

        try {
            caseService.verifyCaseAccess(caseId, principal.getName());

            java.io.InputStream inputStream = storageService.downloadImageContent(caseId, imageId);
            InputStreamResource resource = new InputStreamResource(inputStream);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM) // Use concrete mime lookup or fallback
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"image_" + imageId + "\"")
                    .body(resource);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error downloading image " + imageId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<List<CaseResponse>> getAllCases(java.security.Principal principal) {
        if (principal == null) {
            logger.error("getAllCases called with null principal");
            return ResponseEntity.status(401).build();
        }
        logger.info("getAllCases requested by principal: {}", principal.getName());
        return ResponseEntity.ok(caseService.getCases(principal.getName()));
    }

    @GetMapping("/next-patient-code")
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<Long> getNextPatientCode() {
        return ResponseEntity.ok(caseService.getNextPatientCode());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<CaseResponse> getCaseById(@PathVariable Long id) {
        return ResponseEntity.ok(caseService.getCaseOrThrow(id));
    }

    @GetMapping("/{id}/ai-result")
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<AIResultResponse> getAIResultByCaseId(@PathVariable Long id) {
        AIResultResponse result = caseService.findAiResultByCaseIdOrNull(id);
        return ResponseEntity.ok(result); // returns 200 with null body if no result
    }

    @PostMapping("/{id}/analyze")
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<?> analyzeCase(@PathVariable Long id) {
        try {
            AIResultResponse result = caseService.analyzeCase(id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.error("Analysis pre-condition failed for case {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "message", e.getMessage(),
                    "type", e.getClass().getSimpleName()));
        } catch (com.morphoaid.backend.exception.NotFoundException e) {
            return ResponseEntity.status(404).body(java.util.Map.of(
                    "message", e.getMessage(),
                    "type", "NotFoundException"));
        } catch (Exception e) {
            logger.error("AI Analysis critical failure for case {}: {} - {}", id, e.getClass().getSimpleName(),
                    e.getMessage(), e);
            return ResponseEntity.status(502).body(java.util.Map.of(
                    "message", "Critical failure: " + e.getMessage(),
                    "type", e.getClass().getSimpleName()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<Void> deleteCase(@PathVariable Long id, java.security.Principal principal) {
        try {
            caseService.deleteCase(id, principal.getName());
            return ResponseEntity.noContent().build();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        } catch (com.morphoaid.backend.exception.NotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error deleting case {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
