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
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.StorageService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

    // Removed to implement Step S1
    /*
     * @PostMapping
     * public ResponseEntity<CaseResponse> uploadCase(...)
     */

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
}
