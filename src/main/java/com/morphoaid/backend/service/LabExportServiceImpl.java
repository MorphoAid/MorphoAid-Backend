package com.morphoaid.backend.service;

import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.CaseStatus;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class LabExportServiceImpl implements LabExportService {

    private static final Logger log = LoggerFactory.getLogger(LabExportServiceImpl.class);

    // CSV column header — defines the exact fields exposed (whitelist)
    private static final String CSV_HEADER = "case_id,image_id,source,case_status,image_filename," +
            "top_class_id,confidence,parasite_stage,drug_exposure,drug_type,created_at";

    // MIME extensions map (mime_type → file extension)
    private static final java.util.Map<String, String> MIME_EXT = java.util.Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png");

    private final CaseRepository caseRepository;
    private final AIResultRepository aiResultRepository;
    private final CaseImageRepository caseImageRepository;
    private final StorageService storageService;

    @Autowired
    public LabExportServiceImpl(CaseRepository caseRepository,
            AIResultRepository aiResultRepository,
            CaseImageRepository caseImageRepository,
            StorageService storageService) {
        this.caseRepository = caseRepository;
        this.aiResultRepository = aiResultRepository;
        this.caseImageRepository = caseImageRepository;
        this.storageService = storageService;
    }

    @Override
    @Transactional(readOnly = true)
    public void streamExport(HttpServletResponse response) {
        // 1. Fetch all eligible cases (ANALYZED + REVIEWED), most-recent first
        List<Case> cases = caseRepository.findByStatusInOrderByCreatedAtDesc(
                Arrays.asList(CaseStatus.ANALYZED, CaseStatus.REVIEWED));

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"export.zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {

            // 2. Build CSV rows in memory (small — metadata only)
            StringBuilder csvRows = new StringBuilder(CSV_HEADER).append("\n");

            for (Case aCase : cases) {
                Long caseId = aCase.getId();

                // --- AI Result ---
                Optional<AIResult> aiOpt = aiResultRepository.findByCaseImageCaseEntityId(caseId);

                // --- Preferred image: AIResult.caseImage if available ---
                CaseImage chosenImage = null;
                if (aiOpt.isPresent() && aiOpt.get().getCaseImage() != null) {
                    chosenImage = aiOpt.get().getCaseImage();
                } else {
                    // Fallback: first CaseImage for this case (most-recently uploaded first)
                    chosenImage = caseImageRepository.findByCaseEntityIdOrderByCreatedAtDesc(caseId)
                            .stream().findFirst().orElse(null);
                }

                if (chosenImage == null) {
                    log.warn("Export: no image found for caseId={}, skipping", caseId);
                    continue;
                }

                Long imageId = chosenImage.getId();
                String ext = MIME_EXT.getOrDefault(chosenImage.getMimeType(), ".jpg");
                String imageFilename = "images/" + caseId + "_" + imageId + ext;

                // --- Write image entry to ZIP ---
                try (InputStream imageStream = storageService.downloadImageContent(caseId, imageId)) {
                    zos.putNextEntry(new ZipEntry(imageFilename));
                    imageStream.transferTo(zos);
                    zos.closeEntry();
                } catch (Exception e) {
                    log.error("Export: failed to fetch image caseId={}, imageId={}: {}", caseId, imageId,
                            e.getMessage());
                    // Skip this case's image but still write its CSV row with empty filename
                }

                // --- Source inference (same as LabReviewServiceImpl) ---
                String source = "UNKNOWN";
                if (aCase.getUploadedBy() != null) {
                    source = Role.DATA_USE.equals(aCase.getUploadedBy().getRole())
                            ? "CLINICAL"
                            : "LAB_UPLOAD";
                }

                // --- CSV row (whitelist only — no PII) ---
                AIResult ai = aiOpt.orElse(null);
                csvRows.append(escapeCsv(String.valueOf(caseId))).append(",")
                        .append(escapeCsv(String.valueOf(imageId))).append(",")
                        .append(escapeCsv(source)).append(",")
                        .append(escapeCsv(aCase.getStatus().name())).append(",")
                        .append(escapeCsv(imageFilename)).append(",")
                        .append(ai != null && ai.getTopClassId() != null ? ai.getTopClassId() : "").append(",")
                        .append(ai != null && ai.getConfidence() != null ? ai.getConfidence() : "").append(",")
                        .append(escapeCsv(ai != null ? ai.getParasiteStage() : "")).append(",")
                        .append(ai != null && ai.getDrugExposure() != null ? ai.getDrugExposure() : "").append(",")
                        .append(escapeCsv(ai != null ? ai.getDrugType() : "")).append(",")
                        .append(aCase.getCreatedAt() != null ? aCase.getCreatedAt() : "")
                        .append("\n");
            }

            // 3. Write labels.csv as last entry
            byte[] csvBytes = csvRows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("labels.csv"));
            zos.write(csvBytes);
            zos.closeEntry();

            zos.finish();

        } catch (IOException e) {
            log.error("Export: failed to write ZIP stream", e);
            // Response is already committed — nothing more we can do
        }
    }

    /** Wraps value in quotes and escapes inner quotes for RFC 4180 CSV. */
    private static String escapeCsv(String value) {
        if (value == null)
            return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
