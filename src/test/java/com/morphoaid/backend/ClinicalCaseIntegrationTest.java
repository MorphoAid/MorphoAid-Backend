package com.morphoaid.backend;

import com.morphoaid.backend.entity.*;
import com.morphoaid.backend.repository.*;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
                "AWS_S3_BUCKET=test-bucket",
                "AWS_S3_REGION=ap-southeast-1",
                "AWS_ACCESS_KEY_ID=mock-key",
                "AWS_SECRET_ACCESS_KEY=mock-secret",
                "ULTRALYTICS_API_KEY=test-api-key"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ClinicalCaseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private CaseRepository caseRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private CaseImageRepository caseImageRepository;

        @Autowired
        private AIResultRepository aiResultRepository;

        @MockBean
        private UltralyticsClient mockUltralyticsClient;

        @MockBean
        private StorageService mockStorageService;

        private User dataUser;
        private User otherUser;

        @BeforeEach
        void setup() {
                aiResultRepository.deleteAll();
                caseImageRepository.deleteAll();
                caseRepository.deleteAll();
                userRepository.deleteAll();

                dataUser = User.builder()
                                .email("datause@test.com")
                                .username("datause")
                                .fullName("Data Use User")
                                .password("password")
                                .role(Role.DATA_USE)
                                .build();
                dataUser = userRepository.save(dataUser);

                otherUser = User.builder()
                                .email("other@test.com")
                                .username("other")
                                .fullName("Other User")
                                .password("password")
                                .role(Role.DATA_USE)
                                .build();
                otherUser = userRepository.save(otherUser);

                when(mockStorageService.uploadCaseImage(anyLong(), any(), any())).thenAnswer(invocation -> {
                        Long caseId = invocation.getArgument(0);
                        User uploader = invocation.getArgument(2);
                        Case aCase = caseRepository.findById(caseId).orElseThrow();
                        CaseImage img = CaseImage.builder()
                                        .caseEntity(aCase)
                                        .uploadedBy(uploader)
                                        .bucket("test")
                                        .objectKey("test-key")
                                        .size(100L)
                                        .mimeType("image/jpeg")
                                        .build();
                        return caseImageRepository.save(img);
                });

                when(mockUltralyticsClient.predict(any(), any())).thenReturn("[]");
        }

        @Test
        @WithMockUser(username = "datause@test.com", roles = "DATA_USE")
        void testUpload_ConsentFalse_PatientMetadataBlocked() throws Exception {
                MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg",
                                new byte[] { 1, 2, 3 });

                mockMvc.perform(multipart("/clinical/upload")
                                .file(image)
                                .param("provinceCode", "BK")
                                .param("provinceName", "Bangkok")
                                .param("consent", "false")
                                .param("patientMetadata", "Sensitive Info"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message")
                                                .value("Patient consent is required to store patient metadata."));
        }

        @Test
        @WithMockUser(username = "datause@test.com", roles = "DATA_USE")
        void testUpload_Success() throws Exception {
                MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg",
                                new byte[] { 1, 2, 3 });

                mockMvc.perform(multipart("/clinical/upload")
                                .file(image)
                                .param("provinceCode", "BK")
                                .param("provinceName", "Bangkok")
                                .param("consent", "true")
                                .param("patientMetadata", "John Doe"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.provinceName").value("Bangkok"))
                                .andExpect(jsonPath("$.patientMetadata").value("John Doe"));

                assertEquals(1, caseRepository.count());
                Case savedCase = caseRepository.findAll().get(0);
                assertEquals(dataUser.getId(), savedCase.getUploadedBy().getId());
                assertEquals("BK", savedCase.getProvinceCode());
        }

        @Test
        @WithMockUser(username = "datause@test.com", roles = "DATA_USE")
        void testGetCase_OwnerOnly() throws Exception {
                Case myCase = Case.builder()
                                .uploadedBy(dataUser)
                                .imagePath("path")
                                .provinceName("Bangkok")
                                .consent(false)
                                .status(CaseStatus.PENDING)
                                .analysisStatus(AnalysisStatus.PENDING)
                                .build();
                myCase = caseRepository.save(myCase);

                Case otherCase = Case.builder()
                                .uploadedBy(otherUser)
                                .imagePath("path")
                                .provinceName("Phuket")
                                .consent(false)
                                .status(CaseStatus.PENDING)
                                .analysisStatus(AnalysisStatus.PENDING)
                                .build();
                otherCase = caseRepository.save(otherCase);

                // Can access my own case
                mockMvc.perform(get("/clinical/cases/" + myCase.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.provinceName").value("Bangkok"));

                // Cannot access other user's case
                mockMvc.perform(get("/clinical/cases/" + otherCase.getId()))
                                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "datause@test.com", roles = "DATA_USE")
        void testExportPdf_OwnerOnly() throws Exception {
                Case otherCase = Case.builder()
                                .uploadedBy(otherUser)
                                .imagePath("path")
                                .provinceName("Phuket")
                                .consent(false)
                                .status(CaseStatus.PENDING)
                                .analysisStatus(AnalysisStatus.PENDING)
                                .build();
                otherCase = caseRepository.save(otherCase);

                mockMvc.perform(get("/clinical/cases/" + otherCase.getId() + "/export"))
                                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "datause@test.com", roles = "DATA_USE")
        void testExportPdf_Success() throws Exception {
                Case myCase = Case.builder()
                                .uploadedBy(dataUser)
                                .imagePath("path")
                                .provinceName("Bangkok")
                                .consent(true)
                                .status(CaseStatus.ANALYZED)
                                .analysisStatus(AnalysisStatus.COMPLETED)
                                .build();
                myCase = caseRepository.save(myCase);

                when(mockStorageService.downloadImageContent(anyLong(), anyLong()))
                                .thenReturn(new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3 }));

                mockMvc.perform(get("/clinical/cases/" + myCase.getId() + "/export"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                                                org.hamcrest.Matchers.containsString("report_")));
        }
}
