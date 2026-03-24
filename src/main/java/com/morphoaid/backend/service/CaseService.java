package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.dto.UserSummary;
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
import com.morphoaid.backend.entity.AnalysisStatus;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.integration.ai.UltralyticsDetection;
import com.morphoaid.backend.integration.ai.UltralyticsParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(CaseService.class);

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AIResultRepository aiResultRepository;
    private final CaseImageRepository caseImageRepository;
    private final UltralyticsClient ultralyticsClient;
    private final UltralyticsParser ultralyticsParser;
    private final StorageService storageService;

    @Autowired
    public CaseService(CaseRepository caseRepository, UserRepository userRepository,
            AIResultRepository aiResultRepository, CaseImageRepository caseImageRepository,
            UltralyticsClient ultralyticsClient, UltralyticsParser ultralyticsParser,
            StorageService storageService) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.aiResultRepository = aiResultRepository;
        this.caseImageRepository = caseImageRepository;
        this.ultralyticsClient = ultralyticsClient;
        this.ultralyticsParser = ultralyticsParser;
        this.storageService = storageService;
    }

    @Transactional
    public CaseResponse createCase(Long patientCode, String imagePath, String technicianId, String location,
            Long uploaderId) {
        // 1. ค้นหา User จาก uploaderId ที่ส่งมา
        User uploadedBy = userRepository.findById(uploaderId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + uploaderId));

        // 2. เมื่อมั่นใจว่ามี User แน่นอน ค่อยสร้าง Case
        String provinceCode = com.morphoaid.backend.constant.ProvinceConstant.getProvinceCode(location);
        Long finalPatientCode = (patientCode != null) ? patientCode : this.getNextPatientCode();

        Case newCase = Case.builder()
                .patientCode(finalPatientCode)
                .imagePath(imagePath)
                .technicianId(technicianId)
                // location field is deprecated but we keep it null or fallback
                .location(null)
                .provinceName(location)
                .provinceCode(provinceCode)
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

        // Mark analysis as PROCESSING immediately
        targetCase.setAnalysisStatus(AnalysisStatus.PROCESSING);
        caseRepository.save(targetCase);

        // Validate image path exists on the Case
        String imagePath = targetCase.getImagePath();
        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("Case has no image path. Upload image before requesting analysis.");
        }

        // Resolve the CaseImage entity from DB (needed for ai_results.image_id FK)
        List<CaseImage> images = caseImageRepository.findByCaseEntityIdOrderByCreatedAtDesc(caseId);
        if (images.isEmpty()) {
            logger.error("No CaseImage record found in DB for case ID: {}. Upload image first.", caseId);
            throw new IllegalArgumentException("Case has no CaseImage; cannot analyze (caseId=" + caseId + ")");
        }
        CaseImage selectedImage = images.get(0);
        logger.info("Using CaseImage ID: {} for analysis of case: {}", selectedImage.getId(), caseId);

        // Read the image file from disk or fallback to S3
        byte[] imageBytes;
        Path path = Paths.get(imagePath);
        if (Files.exists(path)) {
            try {
                imageBytes = Files.readAllBytes(path);
                logger.info("Read {} bytes from image file: {}", imageBytes.length, imagePath);
            } catch (IOException e) {
                logger.warn("Failed to read image file from disk: {}. Falling back to S3.", imagePath);
                imageBytes = downloadFromStorage(caseId, selectedImage.getId());
            }
        } else {
            logger.info("Image file not found on disk: {}. Fetching from storage.", imagePath);
            imageBytes = downloadFromStorage(caseId, selectedImage.getId());
        }

        // Call the AI model (has fallback to mock if API fails)
        logger.info("Calling Ultralytics for Case ID: {}", caseId);
        String rawResponse = ultralyticsClient.predict(imageBytes, imagePath);
        logger.info("AI response received (length: {})", rawResponse != null ? rawResponse.length() : 0);

        // Parse + map the top detection
        Optional<UltralyticsDetection> detectionOpt;
        try {
            detectionOpt = ultralyticsParser.parseTopDetection(rawResponse);
            logger.info("Parsing successful. Detection optional present: {}", detectionOpt.isPresent());
        } catch (Exception e) {
            logger.error("Parsing failed for response: {}", rawResponse);
            throw e;
        }

        // Re-use existing AI result if present (prevents duplicate key constraint
        // violation)
        AIResult aiResult = aiResultRepository.findByCaseImageCaseEntityId(caseId)
                .orElse(new AIResult());
        aiResult.setCaseImage(selectedImage);
        aiResult.setCaseEntity(targetCase);
        aiResult.setRawResponseJson(rawResponse);

        if (detectionOpt.isPresent()) {
            UltralyticsDetection detection = detectionOpt.get();
            aiResult.setConfidence(detection.confidence());
            aiResult.setDrugExposure(detection.drugExposure());
            aiResult.setDrugType(detection.drugType());
            aiResult.setParasiteStage(detection.parasiteStage());
            aiResult.setTopClassId(detection.topClassId());
            logger.info("Detection mapping: class={}, conf={}", detection.topClassId(), detection.confidence());
        } else {
            aiResult.setConfidence(0.0);
            aiResult.setDrugExposure(false);
            logger.info("No detections found in response.");
        }

        // Save AI Result
        try {
            logger.info("Saving AI Result to DB (image_id={})...", selectedImage.getId());
            aiResult = aiResultRepository.save(aiResult);
            logger.info("AI Result saved successfully with ID: {}", aiResult.getId());
        } catch (Exception e) {
            logger.error("Failed to save AI Result: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            targetCase.setAnalysisStatus(AnalysisStatus.FAILED);
            caseRepository.save(targetCase);
            throw e;
        }

        // Update Case status
        targetCase.setStatus(CaseStatus.ANALYZED);
        targetCase.setAnalysisStatus(AnalysisStatus.COMPLETED);
        caseRepository.save(targetCase);

        return toAIResultResponse(aiResult);
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> getCases(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + userEmail));

        logger.info("Fetching cases for user: {} (ID: {}), role: {}", userEmail, user.getId(), user.getRole());
        List<Case> cases;
        if (user.getRole() == Role.ADMIN) {
            cases = caseRepository.findAllByOrderByIdDesc();
            logger.info("Admin user fetching all {} cases", cases.size());
        } else {
            cases = caseRepository.findAllByUploadedByOrderByIdDesc(user);
            long totalSystemCases = caseRepository.count();
            logger.info(
                    "User {} (ID: {}) fetching their own {} cases (out of {} total system cases). User object UID: {}",
                    userEmail, user.getId(), cases.size(), totalSystemCases, user.getEmail());
            if (cases.isEmpty()) {
                logger.warn(
                        "No cases found for user: {} (ID: {}). Possible ID mismatch or database data missing 'uploaded_by' link.",
                        userEmail, user.getId());
            }
        }

        return cases.stream()
                .map(this::toCaseResponse)
                .collect(Collectors.toList());
    }

    public Long getNextPatientCode() {
        return caseRepository.findMaxPatientCode().orElse(0L) + 1;
    }

    public Optional<CaseResponse> getCaseById(Long id) {
        return caseRepository.findById(id).map(this::toCaseResponse);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCaseOrThrow(Long id) {
        return caseRepository.findById(id)
                .map(this::toCaseResponse)
                .orElseThrow(() -> new NotFoundException("Case not found with id: " + id));
    }

    public Optional<AIResultResponse> getAIResultByCaseId(Long caseId) {
        return aiResultRepository.findByCaseImageCaseEntityId(caseId).map(this::toAIResultResponse);
    }

    public AIResultResponse findAiResultByCaseId(Long caseId) {
        return aiResultRepository.findByCaseImageCaseEntityId(caseId)
                .map(this::toAIResultResponse)
                .orElseThrow(() -> new NotFoundException("AI result not found"));
    }

    public AIResultResponse findAiResultByCaseIdOrNull(Long caseId) {
        return aiResultRepository.findByCaseImageCaseEntityId(caseId)
                .map(this::toAIResultResponse)
                .orElse(null);
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

    @Transactional
    public void deleteCase(Long caseId, String userEmail) {
        verifyCaseAccess(caseId, userEmail);

        // Delete AI results first (no cascade from Case → AIResult)
        aiResultRepository.deleteByCaseEntityId(caseId);

        // Delete the case (cascades to CaseImage and CaseNote)
        caseRepository.deleteById(caseId);
        logger.info("Deleted case with ID: {} by user: {}", caseId, userEmail);
    }

    private CaseResponse toCaseResponse(Case entity) {
        CaseImage image = entity.getImage();

        // Robust check: Hibernate @OneToOne lazy loading on the non-owning side (Case)
        // often returns null even if a record exists in case_images.
        if (image == null && entity.getId() != null) {
            List<CaseImage> images = caseImageRepository.findByCaseEntityIdOrderByCreatedAtDesc(entity.getId());
            if (!images.isEmpty()) {
                image = images.get(0);
            }
        }

        if (image != null) {
            logger.debug("Mapping DTO for Case {}: Image ID={}, Original Filename={}",
                    entity.getId(), image.getId(), image.getOriginalFilename());
        } else {
            logger.debug("Mapping DTO for Case {}: No image associated.", entity.getId());
        }

        return CaseResponse.builder()
                .id(entity.getId())
                .patientCode(entity.getPatientCode())
                .technicianId(entity.getTechnicianId())
                .location(entity.getLocation())
                .provinceCode(entity.getProvinceCode())
                .provinceName(entity.getProvinceName())
                .patientMetadata(entity.getPatientMetadata())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .analysisStatus(entity.getAnalysisStatus() != null ? entity.getAnalysisStatus().name() : null)
                .imagePath(entity.getImagePath())
                .imageId(image != null ? image.getId() : null)
                .imageFilename(image != null ? image.getOriginalFilename() : null)
                .createdAt(entity.getCreatedAt())
                .uploadedById(entity.getUploadedBy() != null ? entity.getUploadedBy().getId() : null)
                .uploadedBy(entity.getUploadedBy() != null ? toUserSummary(entity.getUploadedBy()) : null)
                .build();
    }

    private UserSummary toUserSummary(User user) {
        if (user == null) return null;
        return UserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(user.getRole())
                .profilePictureUrl(user.getProfilePictureUrl())
                .approved(user.isApproved())
                .build();
    }

    private AIResultResponse toAIResultResponse(AIResult entity) {
        return AIResultResponse.builder()
                .id(entity.getId())
                .caseId(entity.getCaseImage() != null && entity.getCaseImage().getCaseEntity() != null
                        ? entity.getCaseImage().getCaseEntity().getId()
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

    private byte[] downloadFromStorage(Long caseId, Long imageId) {
        try (java.io.InputStream s3Is = storageService.downloadImageContent(caseId, imageId)) {
            return s3Is.readAllBytes();
        } catch (Exception e) {
            logger.error("Failed to download image from storage for case {}: {}", caseId, e.getMessage());
            throw new IllegalArgumentException("Image file not found and could not be retrieved from storage", e);
        }
    }

}
