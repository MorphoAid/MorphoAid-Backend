package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.CaseService;
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

    @Autowired
    public CaseController(CaseService caseService, StorageService storageService, UserRepository userRepository) {
        this.caseService = caseService;
        this.storageService = storageService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<CaseResponse> uploadCase(
            @RequestParam("patientCode") String patientCode,
            @RequestParam("technicianId") String technicianId,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam("uploaderId") Long uploaderId,
            @RequestParam("image") MultipartFile image) {

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

        // Generate dummy imagePath (no real file storage yet)
        String dummyImagePath = "/storage/dummy/" + UUID.randomUUID() + "-" + originalFilename;

        try {
            // Call caseService.createCase (now returns DTO)
            CaseResponse newCase = caseService.createCase(patientCode, dummyImagePath, technicianId, location,
                    uploaderId);

            logger.info("Created new case with ID: {}", newCase.getId());

            // Return DTO directly
            return ResponseEntity.ok(newCase);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error creating case", e);
            return ResponseEntity.internalServerError().build();
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
            caseService.verifyCaseAccess(caseId, principal.getName());

            User uploader = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Uploader not found"));

            var caseImage = storageService.uploadCaseImage(caseId, image, uploader);

            return ResponseEntity.ok().body(caseImage);

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
    public ResponseEntity<List<CaseResponse>> getAllCases() {
        return ResponseEntity.ok(caseService.getCases());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> getCaseById(@PathVariable Long id) {
        return ResponseEntity.ok(caseService.getCaseOrThrow(id));
    }

    @GetMapping("/{id}/ai-result")
    public ResponseEntity<AIResultResponse> getAIResultByCaseId(@PathVariable Long id) {
        return ResponseEntity.ok(caseService.findAiResultByCaseId(id));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<AIResultResponse> analyzeCase(@PathVariable Long id) {
        try {
            AIResultResponse result = caseService.analyzeCase(id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("Analysis failed due to missing image: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (com.morphoaid.backend.exception.NotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("AI Analysis failed for case {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(502).build(); // 502 Bad Gateway for upstream AI failure
        }
    }
}
