package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.dto.ClinicalCaseResponse;
import com.morphoaid.backend.entity.*;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.integration.ai.UltralyticsDetection;
import com.morphoaid.backend.integration.ai.UltralyticsParser;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseNoteRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.service.ClinicalCaseServiceImpl;
import com.morphoaid.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class CaseCreationUtc02ServiceTest {

    @Mock
    private CaseRepository caseRepository;
    @Mock
    private AIResultRepository aiResultRepository;
    @Mock
    private CaseNoteRepository caseNoteRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private UltralyticsClient ultralyticsClient;
    @Mock
    private UltralyticsParser ultralyticsParser;

    @InjectMocks
    private ClinicalCaseServiceImpl clinicalCaseService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private User createMockUser() {
        return User.builder().id(1L).email("doctor@test.com").role(Role.DATA_USE).build();
    }

    @Test
    public void UTC_02_TC_01_uploadCase_success() throws Exception {
        // UTC-02-TD-01
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());
        User uploader = createMockUser();

        Case savedCase = Case.builder().id(100L).status(CaseStatus.PENDING).analysisStatus(AnalysisStatus.PENDING).build();
        when(caseRepository.save(any(Case.class))).thenReturn(savedCase);

        CaseImage caseImage = CaseImage.builder().id(200L).objectKey("cases/100/test.jpg").build();
        when(storageService.uploadCaseImage(anyLong(), any(), any())).thenReturn(caseImage);

        when(ultralyticsClient.predict(any(), anyString())).thenReturn("{\"raw\":\"json\"}");
        when(ultralyticsParser.parseTopDetection(anyString())).thenReturn(Optional.of(new UltralyticsDetection(1, 0.95, true, "Artemisinin", "Ring", "{\"raw\":\"json\"}")));

        ClinicalCaseResponse response = clinicalCaseService.uploadCase(image, "10", "Bangkok", true, "Patient A", uploader);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        // Verify state transitions in real code units
        verify(caseRepository, atLeastOnce()).save(any(Case.class));
        verify(storageService).uploadCaseImage(eq(100L), any(), eq(uploader));
        verify(ultralyticsClient).predict(any(), eq("cases/100/test.jpg"));
        verify(aiResultRepository).save(any(AIResult.class));
    }

    @Test
    public void UTC_02_TC_06_uploadCase_storageFails() throws Exception {
        // UTC-02-TD-07 in plan (Storage Failure)
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());
        User uploader = createMockUser();

        Case savedCase = Case.builder().id(101L).build();
        when(caseRepository.save(any(Case.class))).thenReturn(savedCase);
        when(storageService.uploadCaseImage(anyLong(), any(), any())).thenThrow(new RuntimeException("S3 Failed"));

        ClinicalCaseResponse response = clinicalCaseService.uploadCase(image, "10", "Bangkok", true, null, uploader);

        // Per ClinicalCaseServiceImpl:88, analysisStatus is set to FAILED on storage/AI error
        assertThat(response.getAnalysisStatus()).isEqualTo("FAILED");
        verify(caseRepository, atLeast(2)).save(any(Case.class));
    }

    @Test
    public void UTC_02_TC_07_uploadCase_aiAnalysisFails() throws Exception {
        // UTC-02-TD-06 in plan (AI Failure)
        MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());
        User uploader = createMockUser();

        Case savedCase = Case.builder().id(102L).status(CaseStatus.PENDING).build();
        when(caseRepository.save(any(Case.class))).thenReturn(savedCase);
        
        CaseImage caseImage = CaseImage.builder().id(201L).objectKey("key").build();
        when(storageService.uploadCaseImage(anyLong(), any(), any())).thenReturn(caseImage);
        
        when(ultralyticsClient.predict(any(), anyString())).thenThrow(new RuntimeException("AI analysis failed"));

        // Service throws RuntimeException which is caught by Controller
        try {
            clinicalCaseService.uploadCase(image, "10", "Bangkok", true, null, uploader);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("AI analysis failed");
        }

        // Verify analysisStatus is FAILED
        verify(caseRepository, atLeastOnce()).save(argThat(c -> c.getAnalysisStatus() == AnalysisStatus.FAILED));
    }
}
