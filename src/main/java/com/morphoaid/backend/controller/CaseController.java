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
import java.util.UUID;

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

        // Generate dummy imagePath (no real file storage yet)
        String dummyImagePath = "/storage/dummy/" + UUID.randomUUID() + "-" + image.getOriginalFilename();

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
}
