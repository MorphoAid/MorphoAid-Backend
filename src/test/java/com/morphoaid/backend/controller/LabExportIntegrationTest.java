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
import com.morphoaid.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LabExportIntegrationTest {

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

    /** Mock S3 to return dummy bytes — avoids real S3 network calls in tests. */
    @MockBean
    private StorageService storageService;

    private Long analyzedCaseId;

    @BeforeEach
    void setUp() throws Exception {
        // Delete in FK dependency order
        aiResultRepository.deleteAll();
        caseImageRepository.deleteAll();
        caseRepository.deleteAll();
        userRepository.deleteAll();

        // Stub S3 download → return 3 bytes (fake PNG-ish)
        byte[] fakeImageBytes = new byte[] { (byte) 0x89, 0x50, 0x4E };
        when(storageService.downloadImageContent(anyLong(), anyLong()))
                .thenReturn(new ByteArrayInputStream(fakeImageBytes));

        // DATA_USE uploader (CLINICAL source)
        User dataUseUser = userRepository.save(User.builder()
                .email("export-du@test.com")
                .password("hashed")
                .role(Role.DATA_USE)
                .username("expuser1")
                .fullName("Export DU User")
                .build());

        // ANALYZED case
        Case analyzedCase = caseRepository.save(Case.builder()
                .imagePath("raw/10/test.jpg")
                .status(CaseStatus.ANALYZED)
                .uploadedBy(dataUseUser)
                .build());
        analyzedCaseId = analyzedCase.getId();

        // Image (must be saved before AIResult to satisfy image_id FK)
        CaseImage testImage = caseImageRepository.save(CaseImage.builder()
                .bucket("test-bucket")
                .objectKey("raw/10/test.jpg")
                .size(3L)
                .mimeType("image/jpeg")
                .checksum("aabbcc")
                .uploadedBy(dataUseUser)
                .caseEntity(analyzedCase)
                .build());

        // AIResult with the image linked
        aiResultRepository.save(AIResult.builder()
                .caseImage(testImage)
                .parasiteStage("RING")
                .drugExposure(false)
                .topClassId(2)
                .confidence(0.91)
                .rawResponseJson("{\"class\":2,\"confidence\":0.91}")
                .build());

        // REVIEWED case (should also be included in export)
        Case reviewedCase = caseRepository.save(Case.builder()
                .imagePath("raw/11/test2.jpg")
                .status(CaseStatus.REVIEWED)
                .uploadedBy(dataUseUser)
                .build());
        CaseImage img2 = caseImageRepository.save(CaseImage.builder()
                .bucket("test-bucket")
                .objectKey("raw/11/test2.jpg")
                .size(3L)
                .mimeType("image/png")
                .checksum("ddeeff")
                .uploadedBy(dataUseUser)
                .caseEntity(reviewedCase)
                .build());
        aiResultRepository.save(AIResult.builder()
                .caseImage(img2)
                .parasiteStage("SCHIZ")
                .drugExposure(false)
                .topClassId(3)
                .confidence(0.85)
                .rawResponseJson("{\"class\":3,\"confidence\":0.85}")
                .build());

        // PENDING case — must NOT appear in export
        caseRepository.save(Case.builder()
                .imagePath("raw/12/test3.jpg")
                .status(CaseStatus.PENDING)
                .uploadedBy(dataUseUser)
                .build());
    }

    // ─── Security ─────────────────────────────────────────────────────────────

    @Test
    void unauthenticated_returns_403() throws Exception {
        mockMvc.perform(post("/lab/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DATA_USE")
    void dataUse_cannot_export() throws Exception {
        mockMvc.perform(post("/lab/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_cannot_export() throws Exception {
        mockMvc.perform(post("/lab/export"))
                .andExpect(status().isForbidden());
    }

    // ─── Happy Path — DATA_PREP ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DATA_PREP")
    void dataPrep_export_returns_200_with_zip_content_type() throws Exception {
        mockMvc.perform(post("/lab/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"export.zip\""));
    }

    @Test
    @WithMockUser(roles = "DATA_PREP")
    void zip_contains_labels_csv() throws Exception {
        MvcResult result = mockMvc.perform(post("/lab/export"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] zipBytes = result.getResponse().getContentAsByteArray();
        assertThat(zipBytes).isNotEmpty();

        boolean foundLabelsCsv = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("labels.csv".equals(entry.getName())) {
                    foundLabelsCsv = true;
                }
                zis.closeEntry();
            }
        }
        assertThat(foundLabelsCsv).as("labels.csv must be present in export.zip").isTrue();
    }

    @Test
    @WithMockUser(roles = "DATA_PREP")
    void zip_contains_at_least_one_image_entry() throws Exception {
        MvcResult result = mockMvc.perform(post("/lab/export"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] zipBytes = result.getResponse().getContentAsByteArray();
        boolean foundImage = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("images/")) {
                    foundImage = true;
                }
                zis.closeEntry();
            }
        }
        assertThat(foundImage).as("At least one image entry (images/*.jpg) must be in zip").isTrue();
    }

    @Test
    @WithMockUser(roles = "DATA_PREP")
    void zip_contains_both_analyzed_and_reviewed_cases() throws Exception {
        MvcResult result = mockMvc.perform(post("/lab/export"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] zipBytes = result.getResponse().getContentAsByteArray();
        int imageCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("images/"))
                    imageCount++;
                zis.closeEntry();
            }
        }
        // ANALYZED + REVIEWED = 2 cases, PENDING case excluded
        assertThat(imageCount).as("Export must include both ANALYZED and REVIEWED cases (not PENDING)").isEqualTo(2);
    }

    // ─── PII / Forbidden Fields in labels.csv ─────────────────────────────────

    @Test
    @WithMockUser(roles = "DATA_PREP")
    void labels_csv_must_not_contain_forbidden_fields() throws Exception {
        MvcResult result = mockMvc.perform(post("/lab/export"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] zipBytes = result.getResponse().getContentAsByteArray();
        String csvContent = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("labels.csv".equals(entry.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    zis.transferTo(baos);
                    csvContent = baos.toString(StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }
        }

        assertThat(csvContent).as("labels.csv must be readable").isNotNull();

        // PII / forbidden fields must NOT appear
        String[] forbidden = {
                "patientCode", "patient_code",
                "technicianId", "technician_id",
                "uploadedBy", "uploaded_by",
                "email", "fullName", "full_name",
                "firstName", "first_name",
                "lastName", "last_name",
                "objectKey", "object_key",
                "bucket",
                "checksum",
                "rawResponseJson", "raw_response_json"
        };
        for (String field : forbidden) {
            assertThat(csvContent)
                    .as("labels.csv must not contain forbidden field: " + field)
                    .doesNotContainIgnoringCase(field);
        }

        // Allowed header fields must be present
        assertThat(csvContent).contains("case_id");
        assertThat(csvContent).contains("image_id");
        assertThat(csvContent).contains("source");
        assertThat(csvContent).contains("case_status");
        assertThat(csvContent).contains("confidence");
        assertThat(csvContent).contains("parasite_stage");
    }
}
