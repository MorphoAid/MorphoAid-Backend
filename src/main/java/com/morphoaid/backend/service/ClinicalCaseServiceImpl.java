package com.morphoaid.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.morphoaid.backend.dto.CaseNoteResponse;
import com.morphoaid.backend.dto.ClinicalCaseResponse;
import com.morphoaid.backend.entity.*;
import com.morphoaid.backend.exception.NotFoundException;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.integration.ai.UltralyticsDetection;
import com.morphoaid.backend.integration.ai.UltralyticsParser;
import com.morphoaid.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ClinicalCaseServiceImpl implements ClinicalCaseService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalCaseServiceImpl.class);

    private final CaseRepository caseRepository;
    private final AIResultRepository aiResultRepository;
    private final CaseNoteRepository caseNoteRepository;
    private final StorageService storageService;
    private final UltralyticsClient ultralyticsClient;
    private final UltralyticsParser ultralyticsParser;

    @Autowired
    public ClinicalCaseServiceImpl(CaseRepository caseRepository,
            AIResultRepository aiResultRepository,
            CaseNoteRepository caseNoteRepository,
            StorageService storageService,
            UltralyticsClient ultralyticsClient,
            UltralyticsParser ultralyticsParser) {
        this.caseRepository = caseRepository;
        this.aiResultRepository = aiResultRepository;
        this.caseNoteRepository = caseNoteRepository;
        this.storageService = storageService;
        this.ultralyticsClient = ultralyticsClient;
        this.ultralyticsParser = ultralyticsParser;
    }

    @Override
    @Transactional
    public ClinicalCaseResponse uploadCase(MultipartFile image, String provinceCode, String provinceName,
            Boolean consent, String patientMetadata, User uploader) {
        // Validation moved to Controller for exact messages, but double check here
        if (image == null || image.isEmpty())
            throw new IllegalArgumentException("Image is required.");
        if (Boolean.FALSE.equals(consent) && patientMetadata != null && !patientMetadata.isBlank()) {
            throw new IllegalArgumentException("Patient consent is required to store patient metadata.");
        }

        // 1. Persist Case first to get caseId
        Case aCase = Case.builder()
                .provinceCode(provinceCode)
                .provinceName(provinceName)
                .consent(consent)
                .patientMetadata(consent ? patientMetadata : null)
                .status(CaseStatus.PENDING)
                .analysisStatus(AnalysisStatus.PENDING)
                .uploadedBy(uploader)
                .imagePath("PENDING") // Placeholder
                .build();
        aCase = caseRepository.save(aCase);

        try {
            // 2. Upload to S3
            CaseImage caseImage = storageService.uploadCaseImage(aCase.getId(), image, uploader);
            aCase.setImagePath(caseImage.getObjectKey()); // Update with actual path or identifier
            aCase = caseRepository.save(aCase);

            // 3. Trigger AI synchronously (could be async, but UC-08 says can be sync now)
            triggerAIAnalysis(aCase, caseImage, image.getBytes());

        } catch (Exception e) {
            log.error("Error during clinical upload/analysis for case {}: {}", aCase.getId(), e.getMessage());
            // Analysis failure doesn't delete the case per guardrail 3
            aCase.setAnalysisStatus(AnalysisStatus.FAILED);
            caseRepository.save(aCase);
        }

        return toClinicalResponse(aCase);
    }

    private void triggerAIAnalysis(Case aCase, CaseImage caseImage, byte[] imageBytes) {
        aCase.setAnalysisStatus(AnalysisStatus.PROCESSING);
        caseRepository.saveAndFlush(aCase);

        try {
            String rawResponse = ultralyticsClient.predict(imageBytes, caseImage.getObjectKey());
            Optional<UltralyticsDetection> detectionOpt = ultralyticsParser.parseTopDetection(rawResponse);

            AIResult aiResult = new AIResult();
            aiResult.setCaseImage(caseImage); // image_id NOT NULL
            aiResult.setRawResponseJson(rawResponse);

            if (detectionOpt.isPresent()) {
                UltralyticsDetection d = detectionOpt.get();
                aiResult.setConfidence(d.confidence());
                aiResult.setDrugExposure(d.drugExposure());
                aiResult.setDrugType(d.drugType());
                aiResult.setParasiteStage(d.parasiteStage());
                aiResult.setTopClassId(d.topClassId());
            } else {
                aiResult.setConfidence(0.0);
                aiResult.setDrugExposure(false);
            }

            aiResultRepository.save(aiResult);

            aCase.setStatus(CaseStatus.ANALYZED);
            aCase.setAnalysisStatus(AnalysisStatus.COMPLETED);
            caseRepository.save(aCase);

        } catch (Exception e) {
            log.error("AI Analysis failed for case {}: {}", aCase.getId(), e.getMessage());
            aCase.setAnalysisStatus(AnalysisStatus.FAILED);
            caseRepository.save(aCase);
            throw new RuntimeException("AI analysis failed. Please try again later.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalCaseResponse getCaseById(Long id, User currentUser) {
        Case aCase = caseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Case not found"));

        verifyOwner(aCase, currentUser);

        return toClinicalResponse(aCase);
    }

    @Override
    @Transactional
    public CaseNoteResponse addNote(Long caseId, String note, User author) {
        Case aCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found"));

        verifyOwner(aCase, author);

        CaseNote caseNote = CaseNote.builder()
                .caseEntity(aCase)
                .note(note)
                .author(author)
                .build();

        caseNote = caseNoteRepository.save(caseNote);

        return toNoteResponse(caseNote);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseNoteResponse> getNotes(Long caseId, User currentUser) {
        Case aCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found"));

        verifyOwner(aCase, currentUser);

        return caseNoteRepository.findByCaseEntityIdOrderByCreatedAtDesc(caseId).stream()
                .map(this::toNoteResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public void exportPdf(Long caseId, OutputStream outputStream, User currentUser) {
        Case aCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found"));

        verifyOwner(aCase, currentUser);

        Document document = new Document();
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 12);

            document.add(new Paragraph("Clinical Case Report", titleFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Case ID: " + String.format("%05d", aCase.getId()), labelFont));
            document.add(new Paragraph(
                    "Created At: " + aCase.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    valueFont));
            document.add(new Paragraph("Province: " + aCase.getProvinceName(), valueFont));

            if (Boolean.TRUE.equals(aCase.getConsent())) {
                document.add(new Paragraph("Patient Metadata: " + aCase.getPatientMetadata(), valueFont));
            }

            document.add(new Paragraph("Status: " + aCase.getStatus(), valueFont));
            document.add(new Paragraph("AI Status: " + aCase.getAnalysisStatus(), valueFont));

            if (aCase.getAnalysisStatus() == AnalysisStatus.COMPLETED) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("AI Results:", labelFont));
                Optional<AIResult> aiOpt = aiResultRepository.findByCaseImageCaseEntityId(aCase.getId());
                if (aiOpt.isPresent()) {
                    AIResult ai = aiOpt.get();
                    document.add(new Paragraph("Parasite Stage: " + ai.getParasiteStage(), valueFont));
                    document.add(new Paragraph("Confidence: " + String.format("%.2f%%", ai.getConfidence() * 100),
                            valueFont));
                    document.add(new Paragraph(
                            "Drug Exposure: " + (ai.getDrugExposure() != null && ai.getDrugExposure() ? "YES" : "NO"),
                            valueFont));
                    document.add(new Paragraph("Drug Type: " + ai.getDrugType(), valueFont));
                }
            } else if (aCase.getAnalysisStatus() == AnalysisStatus.FAILED) {
                document.add(new Paragraph("AI Result: FAILED", valueFont));
            } else {
                document.add(new Paragraph("AI Result: PENDING/PROCESSING", valueFont));
            }

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Diagnostic Notes:", labelFont));
            List<CaseNote> notes = caseNoteRepository.findByCaseEntityIdOrderByCreatedAtDesc(aCase.getId());
            for (CaseNote note : notes) {
                document.add(new Paragraph(
                        "- " + note.getNote() + " (by " + note.getAuthor().getFullName() + " at "
                                + note.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ")",
                        valueFont));
            }

            document.close();
        } catch (DocumentException e) {
            log.error("Error generating PDF for case {}: {}", caseId, e.getMessage());
            throw new RuntimeException("Error exporting report. Please try again later.", e);
        }
    }

    private void verifyOwner(Case aCase, User user) {
        if (user.getRole() == Role.ADMIN)
            return;
        if (aCase.getUploadedBy() == null || !aCase.getUploadedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("User does not have access to this case");
        }
    }

    private ClinicalCaseResponse toClinicalResponse(Case aCase) {
        return ClinicalCaseResponse.builder()
                .id(aCase.getId())
                .status(aCase.getStatus() != null ? aCase.getStatus().name() : null)
                .analysisStatus(aCase.getAnalysisStatus() != null ? aCase.getAnalysisStatus().name() : null)
                .imageId(aCase.getImage() != null ? aCase.getImage().getId() : null)
                .provinceCode(aCase.getProvinceCode())
                .provinceName(aCase.getProvinceName())
                .consent(aCase.getConsent())
                .patientMetadata(Boolean.TRUE.equals(aCase.getConsent()) ? aCase.getPatientMetadata() : null)
                .createdAt(aCase.getCreatedAt())
                .build();
    }

    private CaseNoteResponse toNoteResponse(CaseNote note) {
        return CaseNoteResponse.builder()
                .id(note.getId())
                .note(note.getNote())
                .authorName(note.getAuthor() != null ? note.getAuthor().getFullName() : "Unknown")
                .createdAt(note.getCreatedAt())
                .build();
    }
}
