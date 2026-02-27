package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.exception.NotFoundException;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AIResultRepository aiResultRepository;

    @Autowired
    public CaseService(CaseRepository caseRepository, UserRepository userRepository,
            AIResultRepository aiResultRepository) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.aiResultRepository = aiResultRepository;
    }

    @Transactional
    public CaseResponse createCase(String patientCode, String imagePath, String technicianId, String location,
            Long uploaderId) {
        // Fetch User
        User uploadedBy = userRepository.findById(uploaderId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + uploaderId));

        // Build Case entity
        Case newCase = Case.builder()
                .patientCode(patientCode)
                .imagePath(imagePath)
                .technicianId(technicianId)
                .location(location)
                .uploadedBy(uploadedBy)
                // status and createdAt are automatically set by @PrePersist
                .build();

        // Save
        Case savedCase = caseRepository.save(newCase);
        return toCaseResponse(savedCase);
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
        // Find AIResult without AI integration logic yet
        return aiResultRepository.findByCaseEntityId(caseId).map(this::toAIResultResponse);
    }

    public AIResultResponse findAiResultByCaseId(Long caseId) {
        return aiResultRepository.findByCaseEntityId(caseId)
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
                .caseId(entity.getCaseEntity() != null ? entity.getCaseEntity().getId() : null)
                .parasiteStage(entity.getParasiteStage())
                .drugExposure(entity.getDrugExposure())
                .confidence(entity.getConfidence())
                .rawResponseJson(entity.getRawResponseJson())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
