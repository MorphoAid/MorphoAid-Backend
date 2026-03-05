package com.morphoaid.backend.repository;

import com.morphoaid.backend.entity.CaseImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseImageRepository extends JpaRepository<CaseImage, Long> {
    List<CaseImage> findByaCaseId(Long caseId);

    /** Used by export image fallback — most-recently uploaded first. */
    List<CaseImage> findByaCaseIdOrderByCreatedAtDesc(Long caseId);
}
