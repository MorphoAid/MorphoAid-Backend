package com.morphoaid.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morphoaid.backend.controller.CaseController;
import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test") // Allows you to override configurations safely
public class AIIntegrationTest {

    @Autowired
    private CaseController caseController;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AIResultRepository aiResultRepository;

    @MockBean
    private UltralyticsClient mockUltralyticsClient;

    private Long savedCaseId;
    private File tempImageFile;

    @BeforeEach
    void setupDatabase() throws Exception {
        aiResultRepository.deleteAll();
        caseRepository.deleteAll();
        userRepository.deleteAll();

        // Create dummy user
        User uploader = new User();
        uploader.setEmail("tester@test.com");
        uploader.setUsername("tester");
        uploader.setFullName("Test User");
        uploader.setPassword("hashedpassword");
        uploader.setRole(com.morphoaid.backend.entity.Role.DATA_USE);
        userRepository.save(uploader);

        // Create dummy physical file since analyzeCase tries to read the bytes directly
        tempImageFile = File.createTempFile("dummy-image", ".jpg");
        Files.write(tempImageFile.toPath(), new byte[] { 1, 2, 3 });

        // Create un-analyzed dummy case tied to explicit temp file path
        Case testCase = Case.builder()
                .patientCode("TEST-AI-01")
                .location("Testing Lab")
                .technicianId("TECH-01")
                .imagePath(tempImageFile.getAbsolutePath())
                .uploadedBy(uploader)
                .build();
        testCase = caseRepository.save(testCase);
        savedCaseId = testCase.getId();
    }

    @Test
    @WithMockUser(roles = "DATA_PREP")
    void testAnalyzeEndpoint_Success() throws Exception { // Arrange Mock JSON Model Response
        String mockResponseJson = """
                [
                  {
                     "boxes": [
                         { "class": 0, "conf": 0.88, "box": {"x1": 10, "y1": 10, "x2": 20, "y2": 20} }
                     ]
                  }
                ]
                """;

        when(mockUltralyticsClient.predict(any(byte[].class), any(String.class)))
                .thenReturn(mockResponseJson);

        // Act
        // This hits the POST /cases/{id}/analyze
        ResponseEntity<AIResultResponse> response = caseController.analyzeCase(savedCaseId);

        // Assert Controller API status
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());

        // Assert Mappings match Class 0 -> DrugExposure(True) & Type(A)
        AIResultResponse aiResult = response.getBody();
        assertEquals(0.88, aiResult.getConfidence());
        assertTrue(aiResult.getDrugExposure());
        assertEquals("A", aiResult.getDrugType());

        // Assert Case status updated successfully to ANALYZED
        Case updatedCase = caseRepository.findById(savedCaseId).orElseThrow();
        assertEquals(CaseStatus.ANALYZED, updatedCase.getStatus());

        // Cleanup temp file
        tempImageFile.delete();
    }
}
