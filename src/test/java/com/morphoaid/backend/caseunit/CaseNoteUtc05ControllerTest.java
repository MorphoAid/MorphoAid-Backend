package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.controller.ClinicalCaseController;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.ClinicalCaseService;
import com.morphoaid.backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaseNoteUtc05ControllerTest {

    @Mock
    private ClinicalCaseService clinicalCaseService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClinicalCaseController clinicalCaseController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_05_TC_02_addNote_blankContent() {
        // UTC-05-TD-03
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("doctor@test.com");
        when(userRepository.findByEmail("doctor@test.com")).thenReturn(Optional.of(User.builder().build()));

        Map<String, String> body = Map.of("note", "  "); // Blank note

        ResponseEntity<?> response = clinicalCaseController.addNote(50L, body, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().toString()).contains("Note content is required.");
    }

    @Test
    public void UTC_05_TC_02_updateNote_blankContent() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("doctor@test.com");
        when(userRepository.findByEmail("doctor@test.com")).thenReturn(Optional.of(User.builder().build()));

        Map<String, String> body = Map.of("note", ""); // Empty note

        ResponseEntity<?> response = clinicalCaseController.updateNote(50L, 10L, body, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
