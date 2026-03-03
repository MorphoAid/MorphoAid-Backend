package com.morphoaid.backend.repository;

import com.morphoaid.backend.entity.AIResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AIResultRepository extends JpaRepository<AIResult, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT a FROM AIResult a WHERE a.image.aCase.id = :caseId")
    Optional<AIResult> findByCaseId(@org.springframework.data.repository.query.Param("caseId") Long caseId);
}
