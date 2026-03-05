package com.morphoaid.backend.repository;

import com.morphoaid.backend.entity.AIResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AIResultRepository extends JpaRepository<AIResult, Long> {

    @Query("SELECT a FROM AIResult a WHERE a.caseImage.caseEntity.id = :caseId")
    Optional<AIResult> findByCaseEntityId(@Param("caseId") Long caseId);

    Optional<AIResult> findByCaseImageCaseEntityId(Long caseId);
}