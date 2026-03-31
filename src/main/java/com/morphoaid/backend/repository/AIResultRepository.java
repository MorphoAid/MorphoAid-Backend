package com.morphoaid.backend.repository;

import com.morphoaid.backend.entity.AIResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIResultRepository extends JpaRepository<AIResult, Long> {

    @Query("SELECT a FROM AIResult a WHERE a.caseImage.caseEntity.id = :caseId")
    Optional<AIResult> findByCaseEntityId(@Param("caseId") Long caseId);

    Optional<AIResult> findByCaseImageCaseEntityId(Long caseId);

    void deleteByCaseEntityId(Long caseId);

    @Query("SELECT AVG(a.confidence) FROM AIResult a")
    Double findAvgConfidence();

    @Query("SELECT a.parasiteStage FROM AIResult a WHERE a.parasiteStage IS NOT NULL GROUP BY a.parasiteStage ORDER BY COUNT(a) DESC")
    List<String> findStagesOrderByCountDesc();

    @Query("SELECT a.parasiteStage, a.confidence, COUNT(a), a.drugType FROM AIResult a GROUP BY a.parasiteStage, a.confidence, a.drugType")
    List<Object[]> findStageConfidenceDistribution();
}