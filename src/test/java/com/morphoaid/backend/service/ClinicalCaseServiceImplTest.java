package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.CaseNoteResponse;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseNote;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.exception.NotFoundException;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.integration.ai.UltralyticsParser;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseNoteRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.entity.AnalysisStatus;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.entity.AIResult;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.dto.ClinicalCaseResponse;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicalCaseServiceImplTest {

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

    private User dataUseUser;
    private Case aCase;

    @BeforeEach
    void setUp() {
        dataUseUser = User.builder()
                .id(1L)
                .email("doctor@medical.com")
                .role(Role.DATA_USE)
                .fullName("Dr. Smith")
                .build();

        aCase = Case.builder()
                .id(100L)
                .uploadedBy(dataUseUser)
                .build();
    }

    // UTC-03-TC-01 Add note successfully with valid note content
    @Test
    void addNote_Success() {
        // Arrange
        String validNoteContent = "Patient shows mild symptoms. Prescribed standard medication.";

        when(caseRepository.findById(100L)).thenReturn(Optional.of(aCase));

        CaseNote savedNote = CaseNote.builder()
                .id(500L)
                .caseEntity(aCase)
                .note(validNoteContent)
                .author(dataUseUser)
                .createdAt(LocalDateTime.now())
                .build();

        when(caseNoteRepository.save(any(CaseNote.class))).thenReturn(savedNote);

        // Act
        CaseNoteResponse response = clinicalCaseService.addNote(100L, validNoteContent, dataUseUser);

        // Assert
        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(validNoteContent, response.getNote());
        assertEquals("Dr. Smith", response.getAuthorName());
        assertNotNull(response.getCreatedAt());

        verify(caseRepository, times(1)).findById(100L);
        verify(caseNoteRepository, times(1)).save(any(CaseNote.class));
    }

    // Additional test (not explicitly in UTC scope but good for coverage): case not
    // found
    @Test
    void addNote_CaseNotFound_ThrowsNotFoundException() {
        // Arrange
        when(caseRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class, () -> {
            clinicalCaseService.addNote(999L, "Test Note", dataUseUser);
        });

        verify(caseNoteRepository, never()).save(any());
    }

    // Additional test (not explicitly in UTC scope but good for coverage): access
    // denied
    @Test
    void addNote_NotUploader_ThrowsAccessDeniedException() {
        // Arrange
        User wrongUser = User.builder().id(2L).role(Role.DATA_USE).build();
        when(caseRepository.findById(100L)).thenReturn(Optional.of(aCase)); // Case was uploaded by dataUseUser (ID: 1)

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> {
            clinicalCaseService.addNote(100L, "Test Note", wrongUser); // Trying to add with wrongUser (ID: 2)
        });

        verify(caseNoteRepository, never()).save(any());
    }

    // UTC-04-TC-01 Export PDF successfully with complete case data
    @Test
    void exportPdf_CompleteData_Success() {
        // Arrange
        Case completeCase = Case.builder()
                .id(200L)
                .uploadedBy(dataUseUser)
                .provinceName("Bangkok")
                .consent(true)
                .patientMetadata("Jane Doe")
                .status(CaseStatus.ANALYZED)
                .analysisStatus(AnalysisStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        when(caseRepository.findById(200L)).thenReturn(Optional.of(completeCase));

        AIResult aiResult = AIResult.builder()
                .parasiteStage("RING")
                .confidence(0.95)
                .drugExposure(true)
                .drugType("A")
                .build();
        when(aiResultRepository.findByCaseImageCaseEntityId(200L)).thenReturn(Optional.of(aiResult));

        CaseNote note = CaseNote.builder()
                .note("Test Note")
                .author(dataUseUser)
                .createdAt(LocalDateTime.now())
                .build();
        when(caseNoteRepository.findByCaseEntityIdOrderByCreatedAtDesc(200L))
                .thenReturn(Collections.singletonList(note));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Act
        clinicalCaseService.exportPdf(200L, outputStream, dataUseUser);

        // Assert
        assertTrue(outputStream.size() > 0, "PDF output stream should contain data");
        verify(caseRepository, times(1)).findById(200L);
        verify(aiResultRepository, times(1)).findByCaseImageCaseEntityId(200L);
        verify(caseNoteRepository, times(1)).findByCaseEntityIdOrderByCreatedAtDesc(200L);
    }

    // UTC-04-TC-02 Handle export when required case data is incomplete
    @Test
    void exportPdf_IncompleteData_Success() {
        // Arrange
        Case incompleteCase = Case.builder()
                .id(201L)
                .uploadedBy(dataUseUser)
                .provinceName("Phuket")
                .consent(false)
                .status(CaseStatus.PENDING)
                .analysisStatus(AnalysisStatus.FAILED)
                .createdAt(LocalDateTime.now())
                .build();

        when(caseRepository.findById(201L)).thenReturn(Optional.of(incompleteCase));
        // Note: For FAILED status, it doesn't query aiResultRepository
        when(caseNoteRepository.findByCaseEntityIdOrderByCreatedAtDesc(201L)).thenReturn(Collections.emptyList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Act
        clinicalCaseService.exportPdf(201L, outputStream, dataUseUser);

        // Assert
        assertTrue(outputStream.size() > 0, "PDF output stream should contain data even if incomplete");
        verify(caseRepository, times(1)).findById(201L);
        verify(aiResultRepository, never()).findByCaseImageCaseEntityId(anyLong());
        verify(caseNoteRepository, times(1)).findByCaseEntityIdOrderByCreatedAtDesc(201L);
    }

    // UTC-02-TC-01 Upload clinical case successfully with valid file and valid
    // metadata
    // UTC-02-TC-05 Upload clinical case successfully when consent is true and
    // metadata is valid
    @Test
    void uploadCase_ValidFileAndMetadata_Success() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("image", "test.jpg", "image/jpeg", "dummy".getBytes());
        CaseImage dummyImage = CaseImage.builder().objectKey("raw/1/test.jpg").build();

        when(caseRepository.save(any(Case.class))).thenAnswer(i -> {
            Case c = i.getArgument(0);
            if (c.getId() == null)
                c.setId(300L);
            return c;
        });

        when(storageService.uploadCaseImage(anyLong(), any(MultipartFile.class), eq(dataUseUser)))
                .thenReturn(dummyImage);
        when(ultralyticsClient.predict(any(byte[].class), anyString())).thenReturn("[]");

        // Act
        ClinicalCaseResponse response = clinicalCaseService.uploadCase(file, "BKK", "Bangkok", true, "Patient Info",
                dataUseUser);

        // Assert
        assertNotNull(response);
        assertEquals("ANALYZED", response.getStatus()); // AI runs synchronously and sets to ANALYZED
        // Actually since we trigger AI sync, status becomes ANALYZED.
        // Wait, the real uploadCase saves it as ANALYZED inside triggerAIAnalysis.
        // Let's verify the repository save calls.
        verify(caseRepository, atLeastOnce()).save(any(Case.class));
        verify(storageService, times(1)).uploadCaseImage(eq(300L), any(MultipartFile.class), eq(dataUseUser));
        verify(ultralyticsClient, times(1)).predict(any(byte[].class), eq("raw/1/test.jpg"));
    }

    // UTC-02-TC-02 Handle invalid file type propagated from storage layer
    @Test
    void uploadCase_InvalidFileTypeFromStorage_HandledAndFailed() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("image", "test.pdf", "application/pdf", "dummy".getBytes());

        when(caseRepository.save(any(Case.class))).thenAnswer(i -> {
            Case c = i.getArgument(0);
            if (c.getId() == null)
                c.setId(301L);
            return c;
        });

        when(storageService.uploadCaseImage(anyLong(), any(MultipartFile.class), eq(dataUseUser)))
                .thenThrow(new IllegalArgumentException("Invalid MIME type"));

        // Act
        ClinicalCaseResponse response = clinicalCaseService.uploadCase(file, "BKK", "Bangkok", true, "Patient Info",
                dataUseUser);

        // Assert
        assertNotNull(response);
        assertEquals("FAILED", response.getAnalysisStatus());
        verify(storageService, times(1)).uploadCaseImage(eq(301L), any(MultipartFile.class), eq(dataUseUser));
        verify(ultralyticsClient, never()).predict(any(), any());
    }

    // UTC-02-TC-03 Handle oversized file propagated from storage layer
    @Test
    void uploadCase_OversizedFileFromStorage_HandledAndFailed() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("image", "large.jpg", "image/jpeg", new byte[10]);

        when(caseRepository.save(any(Case.class))).thenAnswer(i -> {
            Case c = i.getArgument(0);
            if (c.getId() == null)
                c.setId(302L);
            return c;
        });

        when(storageService.uploadCaseImage(anyLong(), any(MultipartFile.class), eq(dataUseUser)))
                .thenThrow(new IllegalArgumentException("File size exceeds limit"));

        // Act
        ClinicalCaseResponse response = clinicalCaseService.uploadCase(file, "BKK", "Bangkok", true, "Patient Info",
                dataUseUser);

        // Assert
        assertNotNull(response);
        assertEquals("FAILED", response.getAnalysisStatus());
        verify(storageService, times(1)).uploadCaseImage(eq(302L), any(MultipartFile.class), eq(dataUseUser));
        verify(ultralyticsClient, never()).predict(any(), any());
    }

    // UTC-02-TC-04 Reject upload when patient metadata is provided without consent
    @Test
    void uploadCase_MetadataWithoutConsent_ThrowsIllegalArgumentException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("image", "test.jpg", "image/jpeg", "dummy".getBytes());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            clinicalCaseService.uploadCase(file, "BKK", "Bangkok", false, "Patient Info", dataUseUser);
        });

        assertEquals("Patient consent is required to store patient metadata.", exception.getMessage());
        verify(caseRepository, never()).save(any());
        verify(storageService, never()).uploadCaseImage(any(), any(), any());
    }
}
