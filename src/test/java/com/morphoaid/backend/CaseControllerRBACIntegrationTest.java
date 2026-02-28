package com.morphoaid.backend;

import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.repository.CaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CaseControllerRBACIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseRepository caseRepository;

    private Long sampleCaseId;

    @Autowired
    private com.morphoaid.backend.repository.AIResultRepository aiResultRepository;

    @BeforeEach
    void setUp() {
        aiResultRepository.deleteAll();
        caseRepository.deleteAll();
        Case sampleCase = Case.builder()
                .patientCode("P001")
                .status(CaseStatus.PENDING)
                .imagePath("test-image.jpg")
                .build();
        caseRepository.save(sampleCase);
        sampleCaseId = sampleCase.getId();
    }

    @Test
    void testUnauthenticated_AccessDenied() throws Exception {
        mockMvc.perform(get("/cases"))
                .andExpect(status().isForbidden()); // Without token via security
    }

    @Test
    @WithMockUser(roles = "DATA_USE")
    void testDataUse_CanReadCases() throws Exception {
        mockMvc.perform(get("/cases"))
                .andExpect(status().isOk());
    }

    // Note: Due to mockmultipart usage constraints and simplicity, testing POST
    // /cases upload with mock user
    // requires a multipart mock request which gets verbose.
    // We can at least test that DATA_USE is forbidden from POST /cases/{id}/analyze

    @Test
    @WithMockUser(roles = "DATA_USE")
    void testDataUse_CannotAnalyze() throws Exception {
        mockMvc.perform(post("/cases/" + sampleCaseId + "/analyze"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DATA_PREP")
    void testDataPrep_CanAnalyze() throws Exception {
        // We expect a valid endpoint hit. Due to no real file or mock AI client in this
        // test,
        // it may throw a 400 or 500/502 from inside the controller logic, but crucially
        // NOT 403 Forbidden.
        mockMvc.perform(post("/cases/" + sampleCaseId + "/analyze"))
                .andExpect(status().isBadRequest()); // It shouldn't be 403.
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdmin_CanReadCases() throws Exception {
        mockMvc.perform(get("/cases"))
                .andExpect(status().isOk());
    }
}
