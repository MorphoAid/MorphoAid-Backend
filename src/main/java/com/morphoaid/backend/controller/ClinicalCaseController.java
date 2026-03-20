package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.CaseNoteResponse;
import com.morphoaid.backend.dto.ClinicalCaseResponse;
import com.morphoaid.backend.dto.UpdatePatientInfoRequest;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.ClinicalCaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/clinical")
public class ClinicalCaseController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalCaseController.class);

    private final ClinicalCaseService clinicalCaseService;
    private final UserRepository userRepository;

    @Autowired
    public ClinicalCaseController(ClinicalCaseService clinicalCaseService, UserRepository userRepository) {
        this.clinicalCaseService = clinicalCaseService;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('DATA_USE')")
    public ResponseEntity<?> uploadCase(
            @RequestParam("image") MultipartFile image,
            @RequestParam("provinceCode") String provinceCode,
            @RequestParam("provinceName") String provinceName,
            @RequestParam("consent") Boolean consent,
            @RequestParam(value = "patientMetadata", required = false) String patientMetadata,
            Principal principal) {

        // 1. Validation messages exact per guardrail
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Image is required."));
        }

        long maxSize = 5 * 1024 * 1024;
        if (image.getSize() > maxSize) {
            return ResponseEntity.badRequest().body(Map.of("message", "File size exceeds the limit."));
        }

        String contentType = image.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png")
                && !contentType.equals("image/jpg"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only JPG/PNG files are allowed."));
        }

        try {
            User currentUser = getCurrentUser(principal);
            ClinicalCaseResponse response = clinicalCaseService.uploadCase(image, provinceCode, provinceName, consent,
                    patientMetadata, currentUser);
            return ResponseEntity.ok(response);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating clinical case", e);
            if (e.getMessage() != null && e.getMessage().contains("AI analysis failed")) {
                return ResponseEntity.ok(Map.of("message", "AI analysis failed. Please try again later."));
            }
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Error creating case. Please try again later."));
        }
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize("hasRole('DATA_USE')")
    public ResponseEntity<ClinicalCaseResponse> getCaseById(@PathVariable Long id, Principal principal) {
        User currentUser = getCurrentUser(principal);
        return ResponseEntity.ok(clinicalCaseService.getCaseById(id, currentUser));
    }

    @PatchMapping("/cases/{id}/patient-info")
    @PreAuthorize("hasRole('DATA_USE')")
    public ResponseEntity<?> updatePatientInfo(
            @PathVariable Long id,
            @RequestBody UpdatePatientInfoRequest request,
            Principal principal) {
        try {
            User currentUser = getCurrentUser(principal);
            ClinicalCaseResponse response = clinicalCaseService.updatePatientInfo(id, request, currentUser);
            return ResponseEntity.ok(Map.of("message", "Patient info updated successfully", "data", response));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating patient info for case {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Error updating patient info."));
        }
    }

    @PostMapping("/cases/{id}/notes")
    @PreAuthorize("hasRole('DATA_USE')")
    public ResponseEntity<?> addNote(@PathVariable Long id, @RequestBody Map<String, String> body,
            Principal principal) {
        String note = body.get("note");
        if (note == null || note.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Note content is required."));
        }

        try {
            User currentUser = getCurrentUser(principal);
            CaseNoteResponse response = clinicalCaseService.addNote(id, note, currentUser);
            return ResponseEntity.ok(Map.of("message", "Note saved successfully.", "data", response));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error saving note for case {}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Error saving note. Please try again later."));
        }
    }

    @GetMapping("/cases/{id}/notes")
    @PreAuthorize("hasRole('DATA_USE')")
    public ResponseEntity<List<CaseNoteResponse>> getNotes(@PathVariable Long id, Principal principal) {
        User currentUser = getCurrentUser(principal);
        return ResponseEntity.ok(clinicalCaseService.getNotes(id, currentUser));
    }

    @GetMapping("/cases/{id}/export")
    @PreAuthorize("hasRole('DATA_USE')")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id, Principal principal) {
        try {
            User currentUser = getCurrentUser(principal);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            clinicalCaseService.exportPdf(id, baos, currentUser);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"report_" + String.format("%05d", id) + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(baos.toByteArray());
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error exporting PDF for case {}", id, e);
            throw new RuntimeException("Error exporting report. Please try again later.");
        }
    }

    private User getCurrentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }
}
