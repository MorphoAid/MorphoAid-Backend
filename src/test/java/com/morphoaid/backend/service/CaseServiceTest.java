package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.integration.ai.UltralyticsDetection;
import com.morphoaid.backend.integration.ai.UltralyticsParser;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private CaseRepository caseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AIResultRepository aiResultRepository;
    @Mock
    private CaseImageRepository caseImageRepository;
    @Mock
    private UltralyticsClient ultralyticsClient;
    @Mock
    private UltralyticsParser ultralyticsParser;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private CaseService caseService;

    private Case testCase;
    private CaseImage testCaseImage;

    @BeforeEach
    void setUp() {
        testCase = Case.builder()
                .id(1L)
                .imagePath("invalid_disk_path.jpg") // Force S3 fallback
                .status(CaseStatus.PENDING)
                .build();

        testCaseImage = CaseImage.builder()
                .id(100L)
                .caseEntity(testCase)
                .build();
    }

    // UTC-01-TC-01, UTC-01-TC-05, UTC-01-TC-06
    @Test
    void analyzeCase_Success_WithValidDetection() {
        // Arrange
        when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));
        when(caseImageRepository.findByCaseEntityIdOrderByCreatedAtDesc(1L))
                .thenReturn(Collections.singletonList(testCaseImage));

        InputStream mockStream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
        when(storageService.downloadImageContent(1L, 100L)).thenReturn(mockStream);

        String mockJsonResponse = "{\"dummy\":\"json\"}";
        when(ultralyticsClient.predict(any(byte[].class), eq("invalid_disk_path.jpg"))).thenReturn(mockJsonResponse);

        UltralyticsDetection mockDetection = new UltralyticsDetection(1, 0.95, true, "A", "RING", mockJsonResponse);
        when(ultralyticsParser.parseTopDetection(mockJsonResponse)).thenReturn(Optional.of(mockDetection));

        when(aiResultRepository.save(any(AIResult.class))).thenAnswer(invocation -> {
            AIResult r = invocation.getArgument(0);
            r.setId(50L);
            return r;
        });

        // Act
        AIResultResponse response = caseService.analyzeCase(1L);

        // Assert
        assertNotNull(response);
        assertEquals(0.95, response.getConfidence());
        assertEquals("RING", response.getParasiteStage());
        assertTrue(response.getDrugExposure());
        assertEquals("A", response.getDrugType());
        assertEquals(1, response.getTopClassId());

        verify(caseRepository).save(argThat(c -> c.getStatus() == CaseStatus.ANALYZED));
    }

    // UTC-01-TC-02 Analyze case with empty or no-detection AI response
    @Test
    void analyzeCase_Success_NoDetection() {
        // Arrange
        when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));
        when(caseImageRepository.findByCaseEntityIdOrderByCreatedAtDesc(1L))
                .thenReturn(Collections.singletonList(testCaseImage));
        when(storageService.downloadImageContent(1L, 100L))
                .thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        when(ultralyticsClient.predict(any(byte[].class), anyString())).thenReturn("[]");
        when(ultralyticsParser.parseTopDetection("[]")).thenReturn(Optional.empty());

        when(aiResultRepository.save(any(AIResult.class))).thenAnswer(i -> {
            AIResult r = i.getArgument(0);
            r.setId(51L);
            return r;
        });

        // Act
        AIResultResponse response = caseService.analyzeCase(1L);

        // Assert
        assertEquals(0.0, response.getConfidence());
        assertFalse(response.getDrugExposure());
        assertNull(response.getParasiteStage());

        verify(caseRepository).save(argThat(c -> c.getStatus() == CaseStatus.ANALYZED));
    }

    // UTC-01-TC-03 Analyze case when image cannot be resolved from storage
    @Test
    void analyzeCase_ImageResolutionFails_ThrowsException() {
        // Arrange
        when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));
        when(caseImageRepository.findByCaseEntityIdOrderByCreatedAtDesc(1L))
                .thenReturn(Collections.singletonList(testCaseImage));
        when(storageService.downloadImageContent(1L, 100L)).thenThrow(new IllegalArgumentException("Storage error"));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            caseService.analyzeCase(1L);
        });

        assertTrue(exception.getMessage().contains("could not be retrieved from storage")
                || exception.getMessage().contains("Storage error"));
        verify(ultralyticsClient, never()).predict(any(byte[].class), anyString());
    }

    // UTC-01-TC-04 Analyze case when AI service throws an exception
    @Test
    void analyzeCase_AIServiceFails_ThrowsException() {
        // Arrange
        when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));
        when(caseImageRepository.findByCaseEntityIdOrderByCreatedAtDesc(1L))
                .thenReturn(Collections.singletonList(testCaseImage));
        when(storageService.downloadImageContent(1L, 100L))
                .thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        when(ultralyticsClient.predict(any(byte[].class), anyString())).thenThrow(new RuntimeException("API Down"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            caseService.analyzeCase(1L);
        });

        assertEquals("API Down", exception.getMessage());
        verify(aiResultRepository, never()).save(any(AIResult.class));
        verify(caseRepository, never()).save(argThat(c -> c.getStatus() == CaseStatus.ANALYZED));
    }
}
