package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.exception.NotFoundException;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.integration.ai.UltralyticsDetection;
import com.morphoaid.backend.integration.ai.UltralyticsParser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AIResultRepository aiResultRepository;
    private final CaseImageRepository caseImageRepository;
    private final UltralyticsClient ultralyticsClient;
    private final UltralyticsParser ultralyticsParser;

    @Autowired
    public CaseService(CaseRepository caseRepository, UserRepository userRepository,
            AIResultRepository aiResultRepository, CaseImageRepository caseImageRepository,
            UltralyticsClient ultralyticsClient, UltralyticsParser ultralyticsParser) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.aiResultRepository = aiResultRepository;
        this.caseImageRepository = caseImageRepository;
        this.ultralyticsClient = ultralyticsClient;
        this.ultralyticsParser = ultralyticsParser;
    }

    @Transactional
    public CaseResponse createCase(String patientCode, String imagePath, String technicianId, String location,
            Long uploaderId) {
        // 1. ค้นหา User จาก uploaderId ที่ส่งมา
        User uploadedBy = userRepository.findById(uploaderId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + uploaderId));

        // 2. เมื่อมั่นใจว่ามี User แน่นอน ค่อยสร้าง Case
        Case newCase = Case.builder()
                .patientCode(patientCode)
                .imagePath(imagePath)
                .technicianId(technicianId)
                .location(location)
                .uploadedBy(uploadedBy)
                .build();

        // 3. บันทึกข้อมูล
        Case savedCase = caseRepository.save(newCase);
        return toCaseResponse(savedCase);
    }

    @Transactional
    public AIResultResponse analyzeCase(Long caseId) {
        Case targetCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found with id: " + caseId));

        if (targetCase.getImage() == null) {
            throw new IllegalArgumentException("Case has no image. Upload image before requesting analysis.");
        }

        // Resolve the CaseImage entity to attach to AIResult.
        // ai_results.image_id is NOT NULL in the live DB schema, so this is mandatory.
        CaseImage selectedImage = caseImageRepository
                .findByaCaseIdOrderByCreatedAtDesc(caseId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Case has no CaseImage; cannot analyze (caseId=" + caseId + ")"));

        byte[] imageBytes;
        try {
            Path path = Paths.get(targetCase.getImagePath());
            imageBytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read image file from path: " + targetCase.getImagePath(), e);
        }

        // Call the AI model
        String rawResponse = ultralyticsClient.predict(imageBytes, targetCase.getImagePath());

        // Parse + map the result
        Optional<UltralyticsDetection> detectionOpt = ultralyticsParser.parseTopDetection(rawResponse);

        AIResult aiResult = new AIResult();
        if (targetCase.getImage() == null) {
            throw new IllegalArgumentException("Case does not have an associated CaseImage to analyze.");
        }
        aiResult.setImage(targetCase.getImage());
        aiResult.setRawResponseJson(rawResponse);
        // *** Critical: always set caseImage so image_id FK constraint is satisfied ***
        aiResult.setCaseImage(selectedImage);

        if (detectionOpt.isPresent()) {
            UltralyticsDetection detection = detectionOpt.get();
            aiResult.setConfidence(detection.confidence());
            aiResult.setDrugExposure(detection.drugExposure());
            aiResult.setDrugType(detection.drugType());
            aiResult.setParasiteStage(detection.parasiteStage());
            aiResult.setTopClassId(detection.topClassId());
        } else {
            // No detections
            aiResult.setConfidence(0.0);
            aiResult.setDrugExposure(false);
        }

        // Save AI Result (image_id will now always be set)
        aiResult = aiResultRepository.save(aiResult);

        // Update Case status
        targetCase.setStatus(CaseStatus.ANALYZED);
        caseRepository.save(targetCase);

        return toAIResultResponse(aiResult);
    }

    public List<CaseResponse> getCases() {
        return caseRepository.findAllByOrderByIdDesc()
                .stream()
                .map(this::toCaseResponse)
                .collect(Collectors.toList());
    }

    public Optional<CaseResponse> getCaseById(Long id) {
        return caseRepository.findById(id).map(this::toCaseResponse);
    }

    public CaseResponse getCaseOrThrow(Long id) {
        return caseRepository.findById(id)
                .map(this::toCaseResponse)
                .orElseThrow(() -> new NotFoundException("Case not found with id: " + id));
    }

    public Optional<AIResultResponse> getAIResultByCaseId(Long caseId) {
        return aiResultRepository.findByCaseId(caseId).map(this::toAIResultResponse);
    }

    public AIResultResponse findAiResultByCaseId(Long caseId) {
        return aiResultRepository.findByCaseId(caseId)
                .map(this::toAIResultResponse)
                .orElseThrow(() -> new NotFoundException("AI result not found"));
    }

    public void verifyCaseAccess(Long caseId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            return;
        }

        Case aCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found with id: " + caseId));

        if (aCase.getUploadedBy() == null || !aCase.getUploadedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("User does not have access to this case");
        }
    }

    private CaseResponse toCaseResponse(Case entity) {
        return CaseResponse.builder()
                .id(entity.getId())
                .patientCode(entity.getPatientCode())
                .technicianId(entity.getTechnicianId())
                .location(entity.getLocation())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .imagePath(entity.getImagePath())
                .createdAt(entity.getCreatedAt())
                .uploadedById(entity.getUploadedBy() != null ? entity.getUploadedBy().getId() : null)
                .build();
    }

    private AIResultResponse toAIResultResponse(AIResult entity) {
        return AIResultResponse.builder()
                .id(entity.getId())
                .caseId(entity.getImage() != null && entity.getImage().getACase() != null
                        ? entity.getImage().getACase().getId()
                        : null)
                .parasiteStage(entity.getParasiteStage())
                .drugExposure(entity.getDrugExposure())
                .drugType(entity.getDrugType())
                .topClassId(entity.getTopClassId())
                .confidence(entity.getConfidence())
                .rawResponseJson(entity.getRawResponseJson())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
