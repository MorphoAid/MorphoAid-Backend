package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.dto.CaseNoteResponse;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CaseNoteUtc05ServiceTest {

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
    public void UTC_05_TC_01_addNote_success() {
        // UTC-05-TD-01 & TD-04
        User doctorA = User.builder().id(100L).role(Role.DATA_USE).build();
        Case aCase = Case.builder().id(50L).uploadedBy(doctorA).build();
        
        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));
        when(caseNoteRepository.save(any(CaseNote.class))).thenAnswer(i -> {
            CaseNote cn = i.getArgument(0);
            cn.setId(1L);
            return cn;
        });

        CaseNoteResponse response = clinicalCaseService.addNote(50L, "Clinical observation", doctorA);

        assertThat(response).isNotNull();
        assertThat(response.getNote()).isEqualTo("Clinical observation");
        verify(caseNoteRepository, times(1)).save(any(CaseNote.class));
    }

    @Test
    public void UTC_05_TC_03_addNote_caseNotFound() {
        when(caseRepository.findById(999L)).thenReturn(Optional.empty());
        User doctor = User.builder().id(100L).role(Role.DATA_USE).build();

        assertThrows(NotFoundException.class, () -> clinicalCaseService.addNote(999L, "Note", doctor));
    }

    @Test
    public void UTC_05_TC_04_addNote_accessDenied() {
        // UTC-05-TD-01 & Role USER (Non-clinical)
        User user = User.builder().id(200L).role(Role.DATA_PREP).build();
        User owner = User.builder().id(100L).build();
        Case aCase = Case.builder().id(50L).uploadedBy(owner).build();
        
        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));

        assertThrows(AccessDeniedException.class, () -> clinicalCaseService.addNote(50L, "Note", user));
    }

    @Test
    public void UTC_05_TC_05_updateNote_success() {
        // UTC-05-TD-06
        User author = User.builder().id(100L).role(Role.DATA_USE).build();
        Case aCase = Case.builder().id(50L).uploadedBy(author).build();
        CaseNote existingNote = CaseNote.builder().id(10L).caseEntity(aCase).author(author).note("Old").build();

        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));
        when(caseNoteRepository.findById(10L)).thenReturn(Optional.of(existingNote));
        when(caseNoteRepository.save(any(CaseNote.class))).thenReturn(existingNote);

        CaseNoteResponse response = clinicalCaseService.updateNote(50L, 10L, "Updated Note", author);

        assertThat(response.getNote()).isEqualTo("Updated Note");
        verify(caseNoteRepository, times(1)).save(existingNote);
    }

    @Test
    public void UTC_05_TC_06_updateNote_forbiddenOtherUser() {
        // UTC-05-TD-05
        User author = User.builder().id(100L).role(Role.DATA_USE).build();
        User otherDoctor = User.builder().id(101L).role(Role.DATA_USE).build();
        
        Case aCase = Case.builder().id(50L).uploadedBy(author).build(); // Other doctor HAS access to case but is NOT author
        CaseNote existingNote = CaseNote.builder().id(10L).caseEntity(aCase).author(author).note("Old").build();

        when(caseRepository.findById(50L)).thenReturn(Optional.of(aCase));
        when(caseNoteRepository.findById(10L)).thenReturn(Optional.of(existingNote));

        assertThrows(AccessDeniedException.class, () -> clinicalCaseService.updateNote(50L, 10L, "Hack", otherDoctor));
    }
}
