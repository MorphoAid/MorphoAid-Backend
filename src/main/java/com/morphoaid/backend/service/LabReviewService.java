package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.LabReviewDto;

import java.util.List;

/**
 * Service interface for the Lab module review endpoints.
 * Returns only anonymized LabReviewDto — no PII, no S3 internals.
 */
public interface LabReviewService {

    /** Returns all cases with status ANALYZED, ordered by createdAt desc. */
    List<LabReviewDto> listAnalyzedCases();

    /**
     * Returns detail for a single ANALYZED case.
     * 
     * @throws com.morphoaid.backend.exception.NotFoundException if not found or not
     *                                                           ANALYZED.
     */
    LabReviewDto getCaseDetail(Long caseId);
}
