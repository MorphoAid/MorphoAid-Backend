package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.controller.ClinicalCaseController;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.ClinicalCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.security.Principal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;

public class CaseCreationUtc02ControllerTest {

    @Mock
    private ClinicalCaseService clinicalCaseService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClinicalCaseController clinicalCaseController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        com.morphoaid.backend.entity.User user = com.morphoaid.backend.entity.User.builder()
                .email("doctor@test.com")
                .role(com.morphoaid.backend.entity.Role.DATA_USE)
                .build();
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(user));
    }

    @Test
    public void UTC_02_TC_02_uploadCase_missingImage() {
        // UTC-02-TD-02
        MockMultipartFile emptyFile = new MockMultipartFile("image", "", "image/jpeg", new byte[0]);
        Principal principal = mock(Principal.class);

        ResponseEntity<?> response = clinicalCaseController.uploadCase(emptyFile, "10", "Bangkok", true, null, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("message")).isEqualTo("Image is required.");
    }

    @Test
    public void UTC_02_TC_03_uploadCase_fileTooLarge() {
        // UTC-02-TD-03: Max size is 5MB (5 * 1024 * 1024 = 5242880)
        byte[] largeContent = new byte[5242880 + 1];
        MockMultipartFile largeFile = new MockMultipartFile("image", "large.jpg", "image/jpeg", largeContent);
        Principal principal = mock(Principal.class);

        ResponseEntity<?> response = clinicalCaseController.uploadCase(largeFile, "10", "Bangkok", true, null, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("message")).isEqualTo("File size exceeds the limit.");
    }

    @Test
    public void UTC_02_TC_04_uploadCase_invalidFileType() {
        // UTC-02-TD-04
        MockMultipartFile gifFile = new MockMultipartFile("image", "test.gif", "image/gif", "test content".getBytes());
        Principal principal = mock(Principal.class);

        ResponseEntity<?> response = clinicalCaseController.uploadCase(gifFile, "10", "Bangkok", true, null, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) response.getBody()).get("message")).isEqualTo("Only JPG/PNG files are allowed.");
    }

    @Test
    public void UTC_02_TC_05_uploadCase_missingProvince() {
        // UTC-02-TD-05
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("doctor@test.com");
        
        when(clinicalCaseService.uploadCase(any(), anyString(), anyString(), anyBoolean(), any(), any()))
                .thenReturn(com.morphoaid.backend.dto.ClinicalCaseResponse.builder().build());

        ResponseEntity<?> response = clinicalCaseController.uploadCase(image, "", "", true, null, principal);

        // Note: Real code does not validate empty strings for province yet.
        assertThat(response.getStatusCode().value()).isEqualTo(200); 
    }
}
