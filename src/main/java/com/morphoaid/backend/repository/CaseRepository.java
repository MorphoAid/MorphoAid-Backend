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

    /** Lab review: returns only ANALYZED cases. */
    List<Case> findByStatusOrderByCreatedAtDesc(CaseStatus status);

    /** Lab detail: ensures case exists AND is in the requested status. */
    Optional<Case> findByIdAndStatus(Long id, CaseStatus status);

    /** Lab export: batch-fetch ANALYZED + REVIEWED cases, newest first. */
    List<Case> findByStatusInOrderByCreatedAtDesc(List<CaseStatus> statuses);
}
