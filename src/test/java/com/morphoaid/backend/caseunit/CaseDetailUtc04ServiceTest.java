package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.dto.CaseNoteResponse;
import com.morphoaid.backend.dto.ClinicalCaseResponse;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseNote;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.exception.NotFoundException;
import com.morphoaid.backend.repository.CaseNoteRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.service.ClinicalCaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class CaseDetailUtc04ServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CaseNoteRepository caseNoteRepository;

    @InjectMocks
    private ClinicalCaseServiceImpl clinicalCaseService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_04_TC_01_getCaseById_success() {
        // UTC-04-TD-01 & TD-03
        User doctor = User.builder().id(100L).role(Role.DATA_USE).build();
        Case aCase = Case.builder().id(50L).patientCode(123L).uploadedBy(doctor).build();
        
        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));

        ClinicalCaseResponse response = clinicalCaseService.getCaseById(50L, doctor);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getPatientCode()).isEqualTo(123L);
    }

    @Test
    public void UTC_04_TC_02_getCaseById_notFound() {
        when(caseRepository.findById(999L)).thenReturn(Optional.empty());
        User doctor = User.builder().id(100L).role(Role.DATA_USE).build();

        assertThrows(NotFoundException.class, () -> clinicalCaseService.getCaseById(999L, doctor));
    }

    @Test
    public void UTC_04_TC_03_getCaseById_accessDenied() {
        // UTC-04-TD-04
        User unauthorizedUser = User.builder().id(200L).role(Role.DATA_PREP).build();
        User uploader = User.builder().id(100L).build();
        Case aCase = Case.builder().id(50L).uploadedBy(uploader).build();

        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));

        // Actual code logic in verifyOwner allows DATA_USE or ADMIN. DATA_PREP is restricted if not owner.
        assertThrows(AccessDeniedException.class, () -> clinicalCaseService.getCaseById(50L, unauthorizedUser));
    }

    @Test
    public void UTC_04_TC_05_getNotes_success() {
        // UTC-04-TD-05
        User doctor = User.builder().id(100L).role(Role.DATA_USE).build();
        Case aCase = Case.builder().id(50L).uploadedBy(doctor).build();
        
        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));
        
        User author = User.builder().firstName("Doctor").lastName("A").fullName("Doctor A").build();
        CaseNote note1 = CaseNote.builder().id(1L).note("Follow up required").author(author).createdAt(LocalDateTime.now()).build();
        CaseNote note2 = CaseNote.builder().id(2L).note("Second note").author(author).createdAt(LocalDateTime.now()).build();
        
        when(caseNoteRepository.findByCaseEntityIdOrderByCreatedAtDesc(50L)).thenReturn(List.of(note1, note2));

        List<CaseNoteResponse> notes = clinicalCaseService.getNotes(50L, doctor);

        assertThat(notes).hasSize(2);
        assertThat(notes.get(0).getAuthorName()).isEqualTo("Doctor A");
        assertThat(notes.get(0).getNote()).isEqualTo("Follow up required");
    }

    @Test
    public void UTC_04_TC_06_toClinicalResponse_mappingCheck() {
        // Specifically check fields from ClinicalCaseResponse
        Case aCase = Case.builder()
                .id(50L)
                .patientCode(123L)
                .status(com.morphoaid.backend.entity.CaseStatus.ANALYZED)
                .analysisStatus(com.morphoaid.backend.entity.AnalysisStatus.COMPLETED)
                .provinceName("Bangkok")
                .provinceCode("10")
                .patientMetadata("Age: 25")
                .consent(true)
                .createdAt(LocalDateTime.now())
                .build();
        
        User doctor = User.builder().id(100L).role(Role.DATA_USE).build();
        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));

        ClinicalCaseResponse response = clinicalCaseService.getCaseById(50L, doctor);

        assertThat(response.getProvinceName()).isEqualTo("Bangkok");
        assertThat(response.getPatientMetadata()).isEqualTo("Age: 25");
        assertThat(response.getStatus()).isEqualTo("ANALYZED");
        assertThat(response.getAnalysisStatus()).isEqualTo("COMPLETED");
        assertThat(response.getConsent()).isTrue();
    }
}
