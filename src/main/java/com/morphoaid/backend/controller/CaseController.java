package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.service.CaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.springframework.security.access.prepost.PreAuthorize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/cases")
public class CaseController {

    private static final Logger logger = LoggerFactory.getLogger(CaseController.class);

    private final CaseService caseService;

    @Autowired
    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DATA_PREP', 'ADMIN')")
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

        try {
            // Save real image path temporarily for analyze demonstration
            java.io.File destDir = new java.io.File(System.getProperty("user.dir"), "debug");
            if (!destDir.exists())
                destDir.mkdirs();

            String safeFilename = UUID.randomUUID() + "-" + image.getOriginalFilename();
            java.io.File destFile = new java.io.File(destDir, safeFilename);
            image.transferTo(destFile);

            String realImagePath = destFile.getAbsolutePath();

            // Call caseService.createCase (now returns DTO)
            CaseResponse newCase = caseService.createCase(patientCode, realImagePath, technicianId, location,
                    uploaderId);

            logger.info("Created new case with ID: {}", newCase.getId());

            // Return DTO directly
            return ResponseEntity.ok(newCase);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Upload fail: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<List<CaseResponse>> getAllCases() {
        return ResponseEntity.ok(caseService.getCases());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<CaseResponse> getCaseById(@PathVariable Long id) {
        return ResponseEntity.ok(caseService.getCaseOrThrow(id));
    }

    @GetMapping("/{id}/ai-result")
    @PreAuthorize("hasAnyRole('DATA_USE', 'DATA_PREP', 'ADMIN')")
    public ResponseEntity<AIResultResponse> getAIResultByCaseId(@PathVariable Long id) {
        return ResponseEntity.ok(caseService.findAiResultByCaseId(id));
    }

    @PostMapping("/{id}/analyze")
    @PreAuthorize("hasAnyRole('DATA_PREP', 'ADMIN')")
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
