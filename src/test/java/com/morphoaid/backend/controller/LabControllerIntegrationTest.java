package com.morphoaid.backend.controller;

import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.repository.UserRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LabControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private CaseRepository caseRepository;
        @Autowired
        private AIResultRepository aiResultRepository;
        @Autowired
        private CaseImageRepository caseImageRepository;
        @Autowired
        private UserRepository userRepository;

        private Long analyzedCaseId;
        private Long pendingCaseId;

        @BeforeEach
        void setUp() {
                // Delete in FK dependency order:
                // ai_results (FK: image_id→case_images, case_id→cases)
                // → case_images (FK: case_id→cases, uploaded_by→users)
                // → cases (FK: uploaded_by→users)
                // → users
                aiResultRepository.deleteAll();
                caseImageRepository.deleteAll();
                caseRepository.deleteAll();
                userRepository.deleteAll();

                // Create a DATA_USE uploader (simulates CLINICAL source)
                User dataUseUser = userRepository.save(User.builder()
                                .email("datause@test.com")
                                .password("hashed")
                                .role(Role.DATA_USE)
                                .username("datauser1")
                                .fullName("Data Use User")
                                .build());

                // ANALYZED case — should appear in /lab/review
                Case analyzedCase = caseRepository.save(Case.builder()
                                .imagePath("raw/1/test.jpg")
                                .status(CaseStatus.ANALYZED)
                                .uploadedBy(dataUseUser)
                                .build());
                analyzedCaseId = analyzedCase.getId();

                // CaseImage must be saved BEFORE AIResult (image_id FK required)
                CaseImage testImage = caseImageRepository.save(CaseImage.builder()
                                .bucket("test-bucket")
                                .objectKey("raw/1/test.jpg")
                                .size(100_000L)
                                .mimeType("image/jpeg")
                                .checksum("abc123")
                                .uploadedBy(dataUseUser)
                                .aCase(analyzedCase)
                                .build());

                // AIResult references the specific image analyzed (image_id NOT NULL in live
                // DB)
                aiResultRepository.save(AIResult.builder()
                                .caseEntity(analyzedCase)
                                .caseImage(testImage)
                                .parasiteStage("RING")
                                .drugExposure(false)
                                .topClassId(2)
                                .confidence(0.91)
                                .rawResponseJson("{\"images\":[{\"results\":[{\"class\":2,\"confidence\":0.91}]}]}")
                                .build());

                // PENDING case — must NOT appear in /lab/review list
                Case pendingCase = caseRepository.save(Case.builder()
                                .imagePath("raw/2/test.jpg")
                                .status(CaseStatus.PENDING)
                                .uploadedBy(dataUseUser)
                                .build());
                pendingCaseId = pendingCase.getId();
        }

        // ─── Access Control ───────────────────────────────────────────────────────

        @Test
        void unauthenticated_returns_403() throws Exception {
                mockMvc.perform(get("/lab/review"))
                                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "DATA_USE")
        void dataUse_cannot_access_lab_review() throws Exception {
                mockMvc.perform(get("/lab/review"))
                                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void admin_cannot_access_lab_review() throws Exception {
                mockMvc.perform(get("/lab/review"))
                                .andExpect(status().isForbidden());
        }

        // ─── Happy Path ───────────────────────────────────────────────────────────

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void dataPrep_list_returns_only_analyzed_cases() throws Exception {
                mockMvc.perform(get("/lab/review"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                // Only ANALYZED cases appear
                                .andExpect(jsonPath("$[0].caseStatus").value("ANALYZED"))
                                // caseId must exist
                                .andExpect(jsonPath("$[0].caseId").exists())
                                // imageId must exist
                                .andExpect(jsonPath("$[0].imageId").exists())
                                // source inferred from DATA_USE uploader
                                .andExpect(jsonPath("$[0].source").value("CLINICAL"))
                                // AI fields mapped
                                .andExpect(jsonPath("$[0].parasiteStage").value("RING"))
                                .andExpect(jsonPath("$[0].drugExposure").value(false))
                                .andExpect(jsonPath("$[0].confidence").value(0.91));
        }

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void dataPrep_detail_returns_analyzed_case() throws Exception {
                mockMvc.perform(get("/lab/review/" + analyzedCaseId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.caseId").value(analyzedCaseId))
                                .andExpect(jsonPath("$.caseStatus").value("ANALYZED"));
        }

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void dataPrep_detail_on_pending_case_returns_404() throws Exception {
                // PENDING case must NOT be accessible via /lab/review/{id}
                mockMvc.perform(get("/lab/review/" + pendingCaseId))
                                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void dataPrep_detail_on_nonexistent_case_returns_404() throws Exception {
                mockMvc.perform(get("/lab/review/999999"))
                                .andExpect(status().isNotFound());
        }

        // ─── PII Assertions — the most important tests ────────────────────────────

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void response_must_not_contain_patientCode() throws Exception {
                mockMvc.perform(get("/lab/review"))
                                .andExpect(jsonPath("$[0].patientCode").doesNotExist());
        }

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void response_must_not_contain_technicianId() throws Exception {
                mockMvc.perform(get("/lab/review"))
                                .andExpect(jsonPath("$[0].technicianId").doesNotExist());
        }

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void response_must_not_contain_s3_internals() throws Exception {
                mockMvc.perform(get("/lab/review"))
                                .andExpect(jsonPath("$[0].objectKey").doesNotExist())
                                .andExpect(jsonPath("$[0].bucket").doesNotExist())
                                .andExpect(jsonPath("$[0].checksum").doesNotExist());
        }

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void response_must_not_contain_user_pii() throws Exception {
                mockMvc.perform(get("/lab/review"))
                                .andExpect(jsonPath("$[0].email").doesNotExist())
                                .andExpect(jsonPath("$[0].uploadedBy").doesNotExist())
                                .andExpect(jsonPath("$[0].fullName").doesNotExist())
                                .andExpect(jsonPath("$[0].firstName").doesNotExist())
                                .andExpect(jsonPath("$[0].lastName").doesNotExist());
        }

        @Test
        @WithMockUser(roles = "DATA_PREP")
        void response_must_not_contain_rawResponseJson() throws Exception {
                // Raw AI JSON is internal — never expose to frontend
                mockMvc.perform(get("/lab/review"))
                                .andExpect(jsonPath("$[0].rawResponseJson").doesNotExist());
        }
}
