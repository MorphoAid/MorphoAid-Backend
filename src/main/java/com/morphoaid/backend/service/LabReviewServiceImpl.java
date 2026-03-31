package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.LabReviewDto;
import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.exception.NotFoundException;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class LabReviewServiceImpl implements LabReviewService {

    private final CaseRepository caseRepository;
    private final AIResultRepository aiResultRepository;
    private final CaseImageRepository caseImageRepository;

    @Autowired
    public LabReviewServiceImpl(CaseRepository caseRepository,
            AIResultRepository aiResultRepository,
            CaseImageRepository caseImageRepository) {
        this.caseRepository = caseRepository;
        this.aiResultRepository = aiResultRepository;
        this.caseImageRepository = caseImageRepository;
    }

    @Override
    public List<LabReviewDto> listAnalyzedCases() {
        return caseRepository
                .findByStatusOrderByCreatedAtDesc(CaseStatus.ANALYZED)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public LabReviewDto getCaseDetail(Long caseId) {
        Case aCase = caseRepository.findByIdAndStatus(caseId, CaseStatus.ANALYZED)
                .orElseThrow(() -> new NotFoundException(
                        "Case not found or not yet analyzed: " + caseId));
        return toDto(aCase);
    }

    // -------------------------------------------------------------------------
    // Private mapping — the ONLY place that touches Case fields.
    // No PII must escape this method into LabReviewDto.
    // -------------------------------------------------------------------------

    private LabReviewDto toDto(Case aCase) {
        // --- source: infer from uploader role (never expose User PII) ---
        String source = "LAB_UPLOAD"; // default for DATA_PREP uploaders
        if (aCase.getUploadedBy() != null
                && Role.DATA_USE.equals(aCase.getUploadedBy().getRole())) {
            source = "CLINICAL";
        }

        // --- AI result (may be absent for PENDING cases that slipped through) ---
        Optional<AIResult> aiResult = aiResultRepository.findByCaseImageCaseEntityId(aCase.getId());

        // --- First image id only (no objectKey / bucket / checksum exposed) ---
        Long imageId = caseImageRepository.findByCaseEntityId(aCase.getId())
                .stream()
                .findFirst()
                .map(CaseImage::getId)
                .orElse(null);

        return new LabReviewDto(
                aCase.getId(),
                aCase.getStatus().name(),
                source,
                aCase.getCreatedAt(),
                imageId,
                aiResult.map(AIResult::getParasiteStage).orElse(null),
                aiResult.map(AIResult::getDrugExposure).orElse(null),
                aiResult.map(AIResult::getDrugType).orElse(null),
                aiResult.map(AIResult::getTopClassId).orElse(null),
                aiResult.map(AIResult::getConfidence).orElse(null));
    }
}
