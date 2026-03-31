package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.controller.ClinicalCaseController;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.ClinicalCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.OutputStream;
import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class CaseExportUtc06ControllerTest {

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
    public void UTC_06_TC_05_exportPdf_controllerSuccess() {
        // UTC-06-TD-01 flow
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("doctor@test.com");
        User user = User.builder().email("doctor@test.com").role(Role.DATA_USE).build();
        when(userRepository.findByEmail("doctor@test.com")).thenReturn(Optional.of(user));

        ResponseEntity<byte[]> response = clinicalCaseController.exportPdf(50L, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION).get(0)).contains("report_00050.pdf");
        
        verify(clinicalCaseService, times(1)).exportPdf(eq(50L), any(OutputStream.class), eq(user));
    }

    @Test
    public void UTC_06_TC_06_exportPdf_controllerInternalError() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("doctor@test.com");
        User user = User.builder().email("doctor@test.com").role(Role.DATA_USE).build();
        when(userRepository.findByEmail("doctor@test.com")).thenReturn(Optional.of(user));

        // Simulate DocumentException wrapped in RuntimeException
        doThrow(new RuntimeException("Export failed")).when(clinicalCaseService).exportPdf(anyLong(), any(), any());

        assertThrows(RuntimeException.class, () -> clinicalCaseController.exportPdf(50L, principal));
    }
}
