package com.morphoaid.backend.service;

import com.morphoaid.backend.constant.ProvinceConstant;
import com.morphoaid.backend.dto.ProvinceDataDto;
import com.morphoaid.backend.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VisualizationService {

    private final CaseRepository caseRepository;

    public List<ProvinceDataDto> getHeatmapData() {
        // Query returns rows: [id, provinceCode, provinceName, location, parasiteStage]
        List<Object[]> rawCases = caseRepository.findRawCasesForHeatmap();

        // Code -> Set of distinct Case IDs
        java.util.Map<String, java.util.Set<Long>> codeToCaseIds = new java.util.HashMap<>();
        // Code -> Case ID -> Representative Parasite Stage
        java.util.Map<String, java.util.Map<Long, String>> codeToCaseStages = new java.util.HashMap<>();

        int unresolvedCasesCount = 0;

        for (Object[] row : rawCases) {
            Long caseId = (Long) row[0];
            String provinceCode = (String) row[1];
            String provinceName = (String) row[2];
            String location = (String) row[3];
            String parasiteStage = (String) row[4];

            // Resolve province code
            String resolvedCode = provinceCode;
            if (resolvedCode == null || resolvedCode.trim().isEmpty()) {
                resolvedCode = ProvinceConstant.getProvinceCode(provinceName);
            }
            if (resolvedCode == null || resolvedCode.trim().isEmpty()) {
                resolvedCode = ProvinceConstant.getProvinceCode(location);
            }

            if (resolvedCode == null || resolvedCode.trim().isEmpty()) {
                unresolvedCasesCount++;
                continue; // Skip case if province cannot be resolved
            }

            // Track distinct case IDs for the province
            codeToCaseIds.computeIfAbsent(resolvedCode, k -> new java.util.HashSet<>()).add(caseId);

            // Assign a representative stage for the case (first one encountered)
            java.util.Map<Long, String> caseStages = codeToCaseStages.computeIfAbsent(resolvedCode,
                    k -> new java.util.HashMap<>());
            if (!caseStages.containsKey(caseId)) {
                caseStages.put(caseId, parasiteStage != null ? parasiteStage : "Unknown");
            }
        }

        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VisualizationService.class);
        logger.info("Heatmap aggregation: {} case rows could not be resolved to a province.", unresolvedCasesCount);

        List<ProvinceDataDto> dataList = new ArrayList<>();

        for (String[] province : ProvinceConstant.PROVINCES) {
            String code = province[2].trim();
            String enName = ProvinceConstant.getEnglishNameByCode(code);
            String thName = ProvinceConstant.getThaiNameByCode(code);

            java.util.Set<Long> uniqueCaseIds = codeToCaseIds.getOrDefault(code, new java.util.HashSet<>());
            int totalValue = uniqueCaseIds.size();

            java.util.Map<String, Integer> categories = new java.util.HashMap<>();

            if (totalValue > 0) {
                java.util.Map<Long, String> caseStages = codeToCaseStages.getOrDefault(code, new java.util.HashMap<>());
                // Calculate categories based on the representative stage for each unique case
                for (Long caseId : uniqueCaseIds) {
                    String stage = caseStages.getOrDefault(caseId, "Unknown");
                    categories.put(stage, categories.getOrDefault(stage, 0) + 1);
                }
            }

            dataList.add(ProvinceDataDto.builder()
                    .provinceNameEn(enName)
                    .provinceNameTh(thName)
                    .value(totalValue)
                    .categories(categories.isEmpty() ? null : categories)
                    .build());
        }

        return dataList;
    }
}
