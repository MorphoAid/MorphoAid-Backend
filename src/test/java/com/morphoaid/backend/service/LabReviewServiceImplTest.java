package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.LabReviewDto;
import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.exception.NotFoundException;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabReviewServiceImplTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private AIResultRepository aiResultRepository;

    @Mock
    private CaseImageRepository caseImageRepository;

    @InjectMocks
    private LabReviewServiceImpl labReviewService;

    private Case analyzedCase;
    private CaseImage caseImage;
    private AIResult aiResult;
    private User uploader;

    @BeforeEach
    void setUp() {
        uploader = User.builder().id(1L).role(Role.DATA_PREP).build();

        analyzedCase = Case.builder()
                .id(100L)
                .status(CaseStatus.ANALYZED)
                .uploadedBy(uploader)
                .createdAt(LocalDateTime.now())
                .build();

        caseImage = CaseImage.builder()
                .id(200L)
                .caseEntity(analyzedCase)
                .build();

        aiResult = AIResult.builder()
                .id(300L)
                .caseImage(caseImage)
                .parasiteStage("RING")
                .drugExposure(true)
                .drugType("A")
                .topClassId(1)
                .confidence(0.95)
                .build();
    }

    // UTC-10-TC-01 Retrieve review items successfully
    @Test
    void listAnalyzedCases_Success() {
        // Arrange
        when(caseRepository.findByStatusOrderByCreatedAtDesc(CaseStatus.ANALYZED))
                .thenReturn(Collections.singletonList(analyzedCase));
        when(aiResultRepository.findByCaseImageCaseEntityId(100L)).thenReturn(Optional.of(aiResult));
        when(caseImageRepository.findByCaseEntityId(100L)).thenReturn(Collections.singletonList(caseImage));

        // Act
        List<LabReviewDto> result = labReviewService.listAnalyzedCases();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        LabReviewDto dto = result.get(0);

        assertEquals(100L, dto.caseId());
        assertEquals("ANALYZED", dto.caseStatus());
        assertEquals("LAB_UPLOAD", dto.source(), "Should be LAB_UPLOAD for DATA_PREP role");
        assertEquals(200L, dto.imageId());
        assertEquals("RING", dto.parasiteStage());
        assertTrue(dto.drugExposure());
        assertEquals(0.95, dto.confidence());

        verify(caseRepository, times(1)).findByStatusOrderByCreatedAtDesc(CaseStatus.ANALYZED);
    }

    // UTC-11-TC-01 Retrieve review detail successfully for valid target
    @Test
    void getCaseDetail_ValidTarget_Success() {
        // Arrange
        when(caseRepository.findByIdAndStatus(100L, CaseStatus.ANALYZED)).thenReturn(Optional.of(analyzedCase));
        when(aiResultRepository.findByCaseImageCaseEntityId(100L)).thenReturn(Optional.of(aiResult));
        when(caseImageRepository.findByCaseEntityId(100L)).thenReturn(Collections.singletonList(caseImage));

        // Act
        LabReviewDto dto = labReviewService.getCaseDetail(100L);

        // Assert
        assertNotNull(dto);
        assertEquals(100L, dto.caseId());
        assertEquals("ANALYZED", dto.caseStatus());
        assertEquals(200L, dto.imageId());
        assertEquals("RING", dto.parasiteStage());

        verify(caseRepository, times(1)).findByIdAndStatus(100L, CaseStatus.ANALYZED);
    }

    // UTC-11-TC-02 Handle invalid review target correctly
    @Test
    void getCaseDetail_InvalidTarget_ThrowsNotFoundException() {
        // Arrange
        when(caseRepository.findByIdAndStatus(999L, CaseStatus.ANALYZED)).thenReturn(Optional.empty());

        // Act & Assert
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            labReviewService.getCaseDetail(999L);
        });

        assertTrue(exception.getMessage().contains("Case not found or not yet analyzed: 999"));
        verify(caseRepository, times(1)).findByIdAndStatus(999L, CaseStatus.ANALYZED);
        verify(aiResultRepository, never()).findByCaseImageCaseEntityId(anyLong());
    }

    // Extra: Test Mapping logic where Clinical Case source maps correctly
    @Test
    void listAnalyzedCases_SourceMapping_ClinicalUser_Success() {
        // Arrange
        User clinicalUser = User.builder().id(2L).role(Role.DATA_USE).build();
        Case clinicalCase = Case.builder()
                .id(101L)
                .status(CaseStatus.ANALYZED)
                .uploadedBy(clinicalUser)
                .build();

        when(caseRepository.findByStatusOrderByCreatedAtDesc(CaseStatus.ANALYZED))
                .thenReturn(Collections.singletonList(clinicalCase));
        when(aiResultRepository.findByCaseImageCaseEntityId(101L)).thenReturn(Optional.empty());
        when(caseImageRepository.findByCaseEntityId(101L)).thenReturn(Collections.emptyList());

        // Act
        List<LabReviewDto> result = labReviewService.listAnalyzedCases();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CLINICAL", result.get(0).source(), "Should be CLINICAL for DATA_USE role");
    }
}
