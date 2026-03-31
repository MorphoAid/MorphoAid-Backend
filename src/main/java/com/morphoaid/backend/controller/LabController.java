package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.LabReviewDto;
import com.morphoaid.backend.service.LabExportService;
import com.morphoaid.backend.service.LabReviewService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Lab module endpoints — accessible only to DATA_PREP role.
 * All responses are anonymized via LabReviewDto (no PII, no S3 internals).
 */
@RestController
@RequestMapping("/lab")
@PreAuthorize("hasRole('DATA_PREP')")
public class LabController {

    private final LabReviewService labReviewService;
    private final LabExportService labExportService;

    @Autowired
    public LabController(LabReviewService labReviewService, LabExportService labExportService) {
        this.labReviewService = labReviewService;
        this.labExportService = labExportService;
    }

    /**
     * GET /lab/review
     * Returns all cases with status ANALYZED, ordered newest-first.
     */
    @GetMapping("/review")
    public ResponseEntity<List<LabReviewDto>> listReviewQueue() {
        return ResponseEntity.ok(labReviewService.listAnalyzedCases());
    }

    /**
     * GET /lab/review/{caseId}
     * Returns detail for a single ANALYZED case.
     * Returns 404 if case not found or not in ANALYZED status.
     */
    @GetMapping("/review/{caseId}")
    public ResponseEntity<LabReviewDto> getCaseDetail(@PathVariable Long caseId) {
        return ResponseEntity.ok(labReviewService.getCaseDetail(caseId));
    }

    /**
     * POST /lab/export
     * Streams export.zip containing images/ entries and labels.csv.
     * Cases included: ANALYZED + REVIEWED.
     * All content is anonymized — no PII exposed.
     */
    @PostMapping("/export")
    public void exportZip(HttpServletResponse response) {
        labExportService.streamExport(response);
    }
}
