package com.morphoaid.backend.repository;

import com.morphoaid.backend.entity.CaseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseNoteRepository extends JpaRepository<CaseNote, Long> {
    List<CaseNote> findByCaseEntityIdOrderByCreatedAtDesc(Long caseId);
}
