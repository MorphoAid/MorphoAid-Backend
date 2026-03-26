package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.entity.*;
import com.morphoaid.backend.exception.NotFoundException;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseNoteRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.service.ClinicalCaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class CaseExportUtc06ServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private AIResultRepository aiResultRepository;

    @Mock
    private CaseNoteRepository caseNoteRepository;

    @InjectMocks
    private ClinicalCaseServiceImpl clinicalCaseService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_06_TC_01_exportPdf_successWithAiAndNotes() {
        // UTC-06-TD-01, TD-04, TD-05
        User doctor = User.builder().id(100L).role(Role.DATA_USE).build();
        Case aCase = Case.builder()
                .id(50L)
                .status(CaseStatus.ANALYZED)
                .analysisStatus(AnalysisStatus.COMPLETED)
                .provinceName("Bangkok")
                .createdAt(LocalDateTime.now())
                .uploadedBy(doctor)
                .build();

        AIResult aiResult = new AIResult();
        aiResult.setParasiteStage("Ring");
        aiResult.setConfidence(0.99);
        aiResult.setDrugExposure(true);
        aiResult.setDrugType("Artemisinin");

        User author = User.builder().fullName("Doctor A").build();
        CaseNote note = CaseNote.builder().note("Confirmed").author(author).createdAt(LocalDateTime.now()).build();

        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));
        when(aiResultRepository.findByCaseImageCaseEntityId(50L)).thenReturn(Optional.of(aiResult));
        when(caseNoteRepository.findByCaseEntityIdOrderByCreatedAtDesc(50L)).thenReturn(List.of(note));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        clinicalCaseService.exportPdf(50L, outputStream, doctor);

        assertThat(outputStream.size()).isGreaterThan(0);
        // We don't validate PDF content, just that it ran to completion and produced bytes.
    }

    @Test
    public void UTC_06_TC_02_exportPdf_successWithoutAi() {
        // UTC-06-TD-02
        User doctor = User.builder().id(100L).role(Role.DATA_USE).build();
        Case aCase = Case.builder()
                .id(50L)
                .status(CaseStatus.PENDING)
                .analysisStatus(AnalysisStatus.PENDING)
                .provinceName("Bangkok")
                .createdAt(LocalDateTime.now())
                .uploadedBy(doctor)
                .build();

        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));
        when(caseNoteRepository.findByCaseEntityIdOrderByCreatedAtDesc(50L)).thenReturn(Collections.emptyList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        clinicalCaseService.exportPdf(50L, outputStream, doctor);

        assertThat(outputStream.size()).isGreaterThan(0);
    }

    @Test
    public void UTC_06_TC_03_exportPdf_caseNotFound() {
        when(caseRepository.findById(999L)).thenReturn(Optional.empty());
        User doctor = User.builder().id(100L).role(Role.DATA_USE).build();

        assertThrows(NotFoundException.class, () -> clinicalCaseService.exportPdf(999L, new ByteArrayOutputStream(), doctor));
    }

    @Test
    public void UTC_06_TC_04_exportPdf_accessDenied() {
        // UTC-06-TD-03
        User unauthorizedUser = User.builder().id(200L).role(Role.DATA_PREP).build();
        User owner = User.builder().id(100L).build();
        Case aCase = Case.builder().id(50L).uploadedBy(owner).build();

        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));

        assertThrows(AccessDeniedException.class, () -> clinicalCaseService.exportPdf(50L, new ByteArrayOutputStream(), unauthorizedUser));
    }
}
