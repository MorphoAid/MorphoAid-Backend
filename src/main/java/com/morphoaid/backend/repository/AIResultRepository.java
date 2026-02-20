package com.morphoaid.backend.repository;

import com.morphoaid.backend.entity.AIResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AIResultRepository extends JpaRepository<AIResult, Long> {
    Optional<AIResult> findByCaseEntityId(Long caseId);
}
