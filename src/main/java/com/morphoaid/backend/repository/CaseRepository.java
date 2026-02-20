package com.morphoaid.backend.repository;

import com.morphoaid.backend.entity.Case;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseRepository extends JpaRepository<Case, Long> {
    List<Case> findAllByOrderByIdDesc();
}
