package com.morphoaid.backend.repository;

import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends JpaRepository<Case, Long> {
    List<Case> findAllByOrderByIdDesc();

    List<Case> findAllByUploadedByOrderByIdDesc(com.morphoaid.backend.entity.User user);

    /** Lab review: returns only ANALYZED cases. */
    List<Case> findByStatusOrderByCreatedAtDesc(CaseStatus status);

    /** Lab detail: ensures case exists AND is in the requested status. */
    Optional<Case> findByIdAndStatus(Long id, CaseStatus status);

    /** Lab export: batch-fetch ANALYZED + REVIEWED cases, newest first. */
    List<Case> findByStatusInOrderByCreatedAtDesc(List<CaseStatus> statuses);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(c.patientCode) FROM Case c")
    Optional<Long> findMaxPatientCode();

    @org.springframework.data.jpa.repository.Query("SELECT c.provinceName, COUNT(c) FROM Case c WHERE c.provinceName IS NOT NULL GROUP BY c.provinceName")
    List<Object[]> countCasesByProvinceName();

    @org.springframework.data.jpa.repository.Query("SELECT c.id, c.provinceCode, c.provinceName, c.location, ai.parasiteStage "
            +
            "FROM Case c " +
            "LEFT JOIN AIResult ai ON ai.caseEntity.id = c.id")
    List<Object[]> findRawCasesForHeatmap();
}
