package com.morphoaid.backend.repository;

import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends JpaRepository<Case, Long> {
    List<Case> findAllByOrderByIdDesc();

    List<Case> findAllByUploadedByOrderByIdDesc(com.morphoaid.backend.entity.User user);

    List<Case> findByStatusOrderByCreatedAtDesc(CaseStatus status);

    Optional<Case> findByIdAndStatus(Long id, CaseStatus status);

    List<Case> findByStatusInOrderByCreatedAtDesc(List<CaseStatus> statuses);

    @Query("SELECT MAX(c.patientCode) FROM Case c")
    Optional<Long> findMaxPatientCode();

    @Query("SELECT c.provinceName, COUNT(c) FROM Case c WHERE c.provinceName IS NOT NULL GROUP BY c.provinceName")
    List<Object[]> countCasesByProvinceName();

    @Query("SELECT c.id, c.provinceCode, c.provinceName, c.location, ai.parasiteStage " +
           "FROM Case c " +
           "LEFT JOIN AIResult ai ON ai.caseEntity.id = c.id")
    List<Object[]> findRawCasesForHeatmap();

    long countByAnalysisStatus(com.morphoaid.backend.entity.AnalysisStatus analysisStatus);

    @Query("SELECT COUNT(DISTINCT c.provinceName) FROM Case c WHERE c.provinceName IS NOT NULL")
    long countDistinctProvinces();

    @Query("SELECT CAST(c.createdAt AS date), COUNT(c) FROM Case c " +
           "WHERE c.analysisStatus = 'COMPLETED' AND (:cutoff IS NULL OR c.createdAt >= :cutoff) " +
           "GROUP BY CAST(c.createdAt AS date) ORDER BY CAST(c.createdAt AS date) ASC")
    List<Object[]> findTrendDaily(@Param("cutoff") LocalDateTime cutoff);
}
