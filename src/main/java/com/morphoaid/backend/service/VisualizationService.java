package com.morphoaid.backend.service;

import com.morphoaid.backend.constant.ProvinceConstant;
import com.morphoaid.backend.dto.ProvinceDataDto;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class VisualizationService {

    private final CaseRepository caseRepository;
    private final AIResultRepository aiResultRepository;

    public Map<String, Object> getSummaryMetrics() {
        // Count all cases that have been analyzed (have an AI result)
        long totalAnalyses = aiResultRepository.count();
        Double avgConf = aiResultRepository.findAvgConfidence();
        List<String> topStages = aiResultRepository.findStagesOrderByCountDesc();
        
        // Count provinces from cases that have geography data
        long activeProvinces = caseRepository.countDistinctProvinces();
        if (activeProvinces == 0 && totalAnalyses > 0) {
            // Fallback for demo data where provinceName might be null but location exists
            activeProvinces = 1; 
        }

        String resolvedTopStage = (topStages != null && !topStages.isEmpty())
                ? resolveHeatmapStage(topStages.get(0), null)
                : "N/A";

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalAnalyses", totalAnalyses);
        summary.put("avgConfidence", avgConf != null ? Math.round(avgConf * 100.0) : 0.0);
        summary.put("topStage", resolvedTopStage);
        summary.put("activeProvinces", activeProvinces);

        return summary;
    }

    public List<Map<String, Object>> getTrendData(Integer days, String groupBy) {
        java.time.LocalDateTime cutoff = (days != null && days > 0)
                ? java.time.LocalDateTime.now().minusDays(days)
                : null;

        List<Object[]> rawDaily = caseRepository.findTrendDaily(cutoff);

        // Aggregate in Java for flexibility across day/week/month
        Map<String, Long> aggregated = new TreeMap<>();
        java.time.format.DateTimeFormatter outputFormatter;

        if ("month".equalsIgnoreCase(groupBy)) {
            outputFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");
        } else if ("week".equalsIgnoreCase(groupBy)) {
            outputFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-'W'ww");
        } else {
            outputFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        }

        for (Object[] row : rawDaily) {
            // row[0] is typically java.sql.Date or similar from CAST(... AS date)
            java.time.LocalDate date;
            if (row[0] instanceof java.sql.Date) {
                date = ((java.sql.Date) row[0]).toLocalDate();
            } else {
                date = java.time.LocalDate.parse(row[0].toString());
            }

            String key = date.format(outputFormatter);
            Long count = (Long) row[1];
            aggregated.put(key, aggregated.getOrDefault(key, 0L) + count);
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (Map.Entry<String, Long> entry : aggregated.entrySet()) {
            Map<String, Object> map = new HashMap<>();
            map.put("date", entry.getKey());
            map.put("count", entry.getValue());
            trendData.add(map);
        }
        return trendData;
    }

    public List<Map<String, Object>> getAiConfidenceData() {
        List<Object[]> results = aiResultRepository.findStageConfidenceDistribution();

        // Key: stage + "|" + range -> count
        Map<String, Long> aggregated = new HashMap<>();

        for (Object[] row : results) {
            String parasiteStage = (String) row[0];
            Double confidence = (Double) row[1];
            Long count = (Long) row[2];
            String drugType = (row.length > 3) ? (String) row[3] : null;

            String resolvedStage = resolveHeatmapStage(parasiteStage, drugType);
            String range = bucketConfidence(confidence);

            String key = resolvedStage + "|" + range;
            aggregated.put(key, aggregated.getOrDefault(key, 0L) + count);
        }

        List<Map<String, Object>> dist = new ArrayList<>();
        for (Map.Entry<String, Long> entry : aggregated.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            Map<String, Object> map = new HashMap<>();
            map.put("stage", parts[0]);
            map.put("confidenceRange", parts[1]);
            map.put("count", entry.getValue());
            dist.add(map);
        }
        return dist;
    }

    private String resolveHeatmapStage(String stage, String drug) {
        if (stage != null) {
            String s = stage.toLowerCase();
            if (s.contains("ring")) return "Ring";
            if (s.contains("schiz")) return "Schiz";
            if (s.contains("troph")) return "Troph";
        }

        if (drug != null && !drug.trim().isEmpty()) {
            String d = drug.toLowerCase();
            if (d.contains("drug a")) return "Drug A";
            if (d.contains("drug b")) return "Drug B";
            return drug;
        }

        return "Unknown";
    }

    private String bucketConfidence(Double conf) {
        if (conf == null) return "Unknown";
        if (conf >= 0.9) return "90% - 100%";
        if (conf >= 0.8) return "80% - 89%";
        if (conf >= 0.7) return "70% - 79%";
        return "Below 70%";
    }

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
