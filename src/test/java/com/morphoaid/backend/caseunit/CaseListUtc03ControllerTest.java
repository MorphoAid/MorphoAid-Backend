package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.controller.CaseController;
import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.service.CaseService;
import com.morphoaid.backend.service.GeminiValidationService;
import com.morphoaid.backend.service.StorageService;
import com.morphoaid.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaseListUtc03ControllerTest {

    @Mock
    private CaseService caseService;

    @Mock
    private StorageService storageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GeminiValidationService geminiValidationService;

    @InjectMocks
    private CaseController caseController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_03_TC_01_getAllCases_successMapperCheck() {
        // UTC-03-TD-03
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("doctor@test.com");

        CaseResponse case1 = CaseResponse.builder()
                .id(10L)
                .patientCode(1001L)
                .analysisStatus("COMPLETED")
                .build();
        
        when(caseService.getCases("doctor@test.com")).thenReturn(List.of(case1));

        ResponseEntity<List<CaseResponse>> response = caseController.getAllCases(principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().size()).isEqualTo(1);
        assertThat(response.getBody().get(0).getId()).isEqualTo(10L);
        assertThat(response.getBody().get(0).getPatientCode()).isEqualTo(1001L);
    }

    @Test
    public void UTC_03_TC_03_getAllCases_emptyList() {
        // UTC-03-TD-05
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("doctor@test.com");

        when(caseService.getCases(anyString())).thenReturn(Collections.emptyList());

        ResponseEntity<List<CaseResponse>> response = caseController.getAllCases(principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    public void UTC_03_TC_04_getAllCases_unauthenticated() {
        ResponseEntity<List<CaseResponse>> response = caseController.getAllCases(null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
