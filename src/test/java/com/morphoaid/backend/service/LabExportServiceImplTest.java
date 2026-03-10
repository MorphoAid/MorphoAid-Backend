package com.morphoaid.backend.service;

import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabExportServiceImplTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private AIResultRepository aiResultRepository;

    @Mock
    private CaseImageRepository caseImageRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private LabExportServiceImpl labExportService;

    private Case aCase1;
    private CaseImage image1;
    private AIResult aiResult1;
    private User clincalUser;

    @BeforeEach
    void setUp() {
        clincalUser = User.builder().id(10L).role(Role.DATA_USE).build();

        aCase1 = Case.builder()
                .id(100L)
                .status(CaseStatus.ANALYZED)
                .uploadedBy(clincalUser)
                .build();

        image1 = CaseImage.builder()
                .id(200L)
                .mimeType("image/jpeg")
                .caseEntity(aCase1)
                .build();

        aiResult1 = AIResult.builder()
                .caseImage(image1)
                .topClassId(1)
                .confidence(0.99)
                .parasiteStage("RING")
                .drugExposure(false)
                .drugType(null)
                .build();
    }

    // UTC-12-TC-01 Collect and stream export data successfully from valid source
    // data
    // UTC-12-TC-02 Generate export output successfully from complete data
    @Test
    void streamExport_Success_WithCompleteData() throws Exception {
        // Arrange
        when(caseRepository.findByStatusInOrderByCreatedAtDesc(Arrays.asList(CaseStatus.ANALYZED, CaseStatus.REVIEWED)))
                .thenReturn(Collections.singletonList(aCase1));
        when(aiResultRepository.findByCaseImageCaseEntityId(100L)).thenReturn(Optional.of(aiResult1));

        InputStream mockImageStream = new ByteArrayInputStream("dummy image content".getBytes());
        when(storageService.downloadImageContent(100L, 200L)).thenReturn(mockImageStream);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        labExportService.streamExport(response);

        // Assert
        assertEquals("application/zip", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("export.zip"));

        // Verify ZIP contents using a ZipInputStream over the response bytes
        byte[] responseBytes = response.getContentAsByteArray();
        assertTrue(responseBytes.length > 0, "Response should not be empty");

        boolean foundImage = false;
        boolean foundCsv = false;
        String csvContent = "";

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(responseBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("images/100_200.jpg")) {
                    foundImage = true;
                    // Read the image content inside the zip
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    zis.transferTo(buffer);
                    assertEquals("dummy image content", buffer.toString());
                } else if (entry.getName().equals("labels.csv")) {
                    foundCsv = true;
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    zis.transferTo(buffer);
                    csvContent = buffer.toString();
                }
            }
        }

        assertTrue(foundImage, "ZIP should contain the image file");
        assertTrue(foundCsv, "ZIP should contain labels.csv");

        // Verify CSV content format based on defined headers and complete data
        String[] lines = csvContent.trim().split("\n");
        assertEquals(2, lines.length, "CSV should have header and 1 data row");
        assertEquals(
                "case_id,image_id,source,case_status,image_filename,top_class_id,confidence,parasite_stage,drug_exposure,drug_type,created_at",
                lines[0].trim());
        assertTrue(lines[1].contains("\"100\",\"200\",\"CLINICAL\",\"ANALYZED\",\"images/100_200.jpg\""));
        assertTrue(lines[1].contains("1,0.99,\"RING\",false,,"));

        verify(caseRepository, times(1)).findByStatusInOrderByCreatedAtDesc(anyList());
        verify(storageService, times(1)).downloadImageContent(100L, 200L);
    }

    // UTC-12-TC-03 Handle export when some required data is missing
    @Test
    void streamExport_Success_WithMissingData() throws Exception {
        // Arrange
        // Scenario 1: Case has no uploadedBy, no AI Result, and image is pulled from
        // fallback (CaseImageRepository)
        Case incompleteCase = Case.builder()
                .id(101L)
                .status(CaseStatus.ANALYZED) // e.g., AI failed, or queued state morphed
                .uploadedBy(null) // Source becomes UNKNOWN
                .build();

        CaseImage fallbackImage = CaseImage.builder()
                .id(201L)
                .mimeType("image/png")
                .caseEntity(incompleteCase)
                .build();

        when(caseRepository.findByStatusInOrderByCreatedAtDesc(Arrays.asList(CaseStatus.ANALYZED, CaseStatus.REVIEWED)))
                .thenReturn(Collections.singletonList(incompleteCase));
        when(aiResultRepository.findByCaseImageCaseEntityId(101L)).thenReturn(Optional.empty()); // No AI Result
        // Fallback kicks in
        when(caseImageRepository.findByCaseEntityIdOrderByCreatedAtDesc(101L))
                .thenReturn(Collections.singletonList(fallbackImage));

        // Let the image fetch throw an exception to verify catch block writes the CSV
        // anyway
        when(storageService.downloadImageContent(101L, 201L)).thenThrow(new RuntimeException("S3 unavailable"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        labExportService.streamExport(response);

        // Assert
        byte[] responseBytes = response.getContentAsByteArray();
        boolean foundImage = false;
        boolean foundCsv = false;
        String csvContent = "";

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(responseBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("images/101_201.png")) {
                    foundImage = true;
                } else if (entry.getName().equals("labels.csv")) {
                    foundCsv = true;
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    zis.transferTo(buffer);
                    csvContent = buffer.toString();
                }
            }
        }

        assertFalse(foundImage, "ZIP should NOT contain the image file because fetch threw an exception");
        assertTrue(foundCsv, "ZIP should STILL contain labels.csv");

        // Verify CSV handles missing data elegantly
        String[] lines = csvContent.trim().split("\n");
        assertEquals(2, lines.length, "CSV should have header and 1 data row");
        assertTrue(lines[1].startsWith("\"101\",\"201\",\"UNKNOWN\",\"ANALYZED\",\"images/101_201.png\","));
    }
}
