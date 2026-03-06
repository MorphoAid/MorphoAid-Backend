package com.morphoaid.backend;

import com.morphoaid.backend.entity.*;
import com.morphoaid.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class VisualizationIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private CaseRepository caseRepository;

        @Autowired
        private AIResultRepository aiResultRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private CaseImageRepository caseImageRepository;

        @BeforeEach
        void setup() {
                aiResultRepository.deleteAll();
                caseImageRepository.deleteAll();
                caseRepository.deleteAll();
                userRepository.deleteAll();

                // Setup test data
                User user = User.builder()
                                .username("testuser")
                                .email("test@test.com")
                                .password("password")
                                .role(Role.DATA_USE)
                                .build();
                user = userRepository.save(user);

                Case case1 = Case.builder()
                                .provinceName("Bangkok")
                                .provinceCode("BK")
                                .status(CaseStatus.ANALYZED)
                                .analysisStatus(AnalysisStatus.COMPLETED)
                                .imagePath("path1")
                                .uploadedBy(user)
                                .build();
                case1 = caseRepository.save(case1);

                CaseImage image1 = CaseImage.builder()
                                .caseEntity(case1)
                                .uploadedBy(user)
                                .bucket("test")
                                .objectKey("key1")
                                .size(100L)
                                .mimeType("image/jpeg")
                                .build();
                image1 = caseImageRepository.save(image1);

                AIResult result1 = AIResult.builder()
                                .caseEntity(case1)
                                .caseImage(image1)
                                .parasiteStage("RING")
                                .build();
                aiResultRepository.save(result1);

                Case case2 = Case.builder()
                                .provinceName("Bangkok")
                                .provinceCode("BK")
                                .status(CaseStatus.ANALYZED)
                                .analysisStatus(AnalysisStatus.COMPLETED)
                                .imagePath("path2")
                                .uploadedBy(user)
                                .build();
                case2 = caseRepository.save(case2);

                CaseImage image2 = CaseImage.builder()
                                .caseEntity(case2)
                                .uploadedBy(user)
                                .bucket("test")
                                .objectKey("key2")
                                .size(100L)
                                .mimeType("image/jpeg")
                                .build();
                image2 = caseImageRepository.save(image2);

                AIResult result2 = AIResult.builder()
                                .caseEntity(case2)
                                .caseImage(image2)
                                .parasiteStage("TROPH")
                                .build();
                aiResultRepository.save(result2);

                Case case3 = Case.builder()
                                .provinceName("Phuket")
                                .provinceCode("PK")
                                .status(CaseStatus.ANALYZED)
                                .analysisStatus(AnalysisStatus.COMPLETED)
                                .imagePath("path3")
                                .uploadedBy(user)
                                .build();
                case3 = caseRepository.save(case3);

                CaseImage image3 = CaseImage.builder()
                                .caseEntity(case3)
                                .uploadedBy(user)
                                .bucket("test")
                                .objectKey("key3")
                                .size(100L)
                                .mimeType("image/jpeg")
                                .build();
                image3 = caseImageRepository.save(image3);

                AIResult result3 = AIResult.builder()
                                .caseEntity(case3)
                                .caseImage(image3)
                                .parasiteStage("RING")
                                .build();
                aiResultRepository.save(result3);
        }

        @Test
        @WithMockUser(roles = "DATA_USE")
        void testGetHeatmapData() throws Exception {
                mockMvc.perform(get("/visualization/heatmap"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                                // Verify Bangkok data
                                .andExpect(jsonPath("$[?(@.provinceNameEn=='Bangkok')].value").value(2))
                                .andExpect(jsonPath("$[?(@.provinceNameEn=='Bangkok')].categories.RING").value(1))
                                .andExpect(jsonPath("$[?(@.provinceNameEn=='Bangkok')].categories.TROPH").value(1))
                                // Verify Phuket data
                                .andExpect(jsonPath("$[?(@.provinceNameEn=='Phuket')].value").value(1))
                                .andExpect(jsonPath("$[?(@.provinceNameEn=='Phuket')].categories.RING").value(1));
        }
}
