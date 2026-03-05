package com.morphoaid.backend;

import com.morphoaid.backend.controller.CaseController;
import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

@SpringBootTest(properties = {
                "AWS_S3_BUCKET=test-bucket",
                "AWS_S3_REGION=us-east-1",
                "AWS_ACCESS_KEY_ID=mock-key",
                "AWS_SECRET_ACCESS_KEY=mock-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Schema conflict - fixing in Feature 2")
public class AIIntegrationTest {

        @Autowired
        private CaseController caseController;

        @Autowired
        private CaseRepository caseRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private AIResultRepository aiResultRepository;

        @Autowired
        private CaseImageRepository caseImageRepository; // เพิ่มอันนี้

        @MockBean
        private UltralyticsClient mockUltralyticsClient;

        private Long savedCaseId;
        private File tempImageFile;

        @BeforeEach
        void setupDatabase() throws Exception {
                // ลบตามลำดับ FK
                aiResultRepository.deleteAll();
                caseImageRepository.deleteAll(); // ลบอันนี้ด้วย
                caseRepository.deleteAll();
                userRepository.deleteAll();

                // 1. สร้าง User
                User uploader = new User();
                uploader.setEmail("tester@test.com");
                uploader.setUsername("tester");
                uploader.setFullName("Test User");
                uploader.setPassword("hashedpassword");
                uploader.setRole(com.morphoaid.backend.entity.Role.DATA_USE);
                userRepository.save(uploader);

                // 2. สร้างไฟล์จำลอง
                tempImageFile = File.createTempFile("dummy-image", ".jpg");
                Files.write(tempImageFile.toPath(), new byte[] { 1, 2, 3 });

                // 3. สร้าง Case
                Case testCase = Case.builder()
                                .patientCode("TEST-AI-01")
                                .location("Testing Lab")
                                .technicianId("TECH-01")
                                .imagePath(tempImageFile.getAbsolutePath())
                                .uploadedBy(uploader)
                                .build();
                testCase = caseRepository.save(testCase);
                savedCaseId = testCase.getId();

                caseImageRepository.save(CaseImage.builder()
                                .caseEntity(testCase)
                                .bucket("test-bucket")
                                .objectKey("raw/test.jpg")
                                .size(3L)
                                .mimeType("image/jpeg")
                                .checksum("010203")
                                .uploadedBy(uploader)
                                .build());
        }

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void testAnalyzeEndpoint_Success() throws Exception {
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
                ResponseEntity<AIResultResponse> response = caseController.analyzeCase(savedCaseId);

                // Assert
                assertTrue(response.getStatusCode().is2xxSuccessful());
                assertNotNull(response.getBody());

                AIResultResponse aiResult = response.getBody();
                assertEquals(0.88, aiResult.getConfidence());
                assertTrue(aiResult.getDrugExposure());
                assertEquals("A", aiResult.getDrugType());

                Case updatedCase = caseRepository.findById(savedCaseId).orElseThrow();
                assertEquals(CaseStatus.ANALYZED, updatedCase.getStatus());

                tempImageFile.delete();
        }
}