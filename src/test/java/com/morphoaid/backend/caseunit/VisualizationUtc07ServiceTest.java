package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.dto.ProvinceDataDto;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.service.VisualizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class VisualizationUtc07ServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private AIResultRepository aiResultRepository;

    @InjectMocks
    private VisualizationService visualizationService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_07_TC_01_getSummaryMetrics_success() {
        // UTC-07-TD-02
        when(aiResultRepository.count()).thenReturn(10L);
        when(aiResultRepository.findAvgConfidence()).thenReturn(0.85);
        when(aiResultRepository.findStagesOrderByCountDesc()).thenReturn(List.of("Trophozoite"));
        when(caseRepository.countDistinctProvinces()).thenReturn(5L);

        Map<String, Object> summary = visualizationService.getSummaryMetrics();

        assertThat(summary.get("totalAnalyses")).isEqualTo(10L);
        assertThat(summary.get("avgConfidence")).isEqualTo(85.0);
        assertThat(summary.get("topStage")).isEqualTo("Troph"); // Mapping logic in resolveHeatmapStage
    }

    @Test
    public void UTC_07_TC_02_getHeatmapData_aggregation() {
        // UTC-07-TD-01
        // row: [id, provinceCode, provinceName, location, parasiteStage]
        List<Object[]> rawRows = new ArrayList<>();
        rawRows.add(new Object[]{50L, "10", "Bangkok", "Loc A", "Ring"});
        rawRows.add(new Object[]{51L, null, "Chiang Mai", null, "Schiz"}); // Resolved to 50 via name

        when(caseRepository.findRawCasesForHeatmap()).thenReturn(rawRows);

        List<ProvinceDataDto> heatmap = visualizationService.getHeatmapData();

        // Bangkok Metropolis (10) should have value 1
        ProvinceDataDto bkk = heatmap.stream().filter(d -> d.getProvinceNameEn().equals("Bangkok Metropolis")).findFirst().get();
        assertThat(bkk.getValue()).isEqualTo(1);
        assertThat(bkk.getCategories()).containsKey("Ring");

        // Chiang Mai (50) should have value 1
        ProvinceDataDto cm = heatmap.stream().filter(d -> d.getProvinceNameEn().equals("Chiang Mai")).findFirst().get();
        assertThat(cm.getValue()).isEqualTo(1);
    }

    @Test
    public void UTC_07_TC_03_getTrendData_weeklyGrouping() {
        // UTC-07-TD-03, TD-04
        List<Object[]> rawTrend = new ArrayList<>();
        rawTrend.add(new Object[]{java.sql.Date.valueOf("2026-03-24"), 5L});
        rawTrend.add(new Object[]{java.sql.Date.valueOf("2026-03-25"), 10L});

        when(caseRepository.findTrendDaily(any())).thenReturn(rawTrend);

        List<Map<String, Object>> trend = visualizationService.getTrendData(7, "week");

        assertThat(trend).isNotEmpty();
        // 2026-03-24/25 are both in week 13
        assertThat(trend.get(0).get("date").toString()).contains("W13");
        assertThat(trend.get(0).get("count")).isEqualTo(15L);
    }

    @Test
    public void UTC_07_TC_04_getAiConfidenceData_distribution() {
        List<Object[]> distRows = new ArrayList<>();
        // parasiteStage, confidence, count, drugType
        distRows.add(new Object[]{"Ring", 0.95, 5L, null});
        distRows.add(new Object[]{"Schiz", 0.85, 3L, null});

        when(aiResultRepository.findStageConfidenceDistribution()).thenReturn(distRows);

        List<Map<String, Object>> dist = visualizationService.getAiConfidenceData();

        assertThat(dist).hasSize(2);
        Map<String, Object> ringDist = dist.stream().filter(m -> m.get("stage").equals("Ring")).findFirst().get();
        assertThat(ringDist.get("confidenceRange")).isEqualTo("90% - 100%");
        assertThat(ringDist.get("count")).isEqualTo(5L);
    }

    @Test
    public void UTC_07_TC_05_visualization_emptyDataHandling() {
        // UTC-07-TD-05
        when(caseRepository.findRawCasesForHeatmap()).thenReturn(Collections.emptyList());
        when(aiResultRepository.count()).thenReturn(0L);
        when(aiResultRepository.findAvgConfidence()).thenReturn(null);
        when(caseRepository.findTrendDaily(any())).thenReturn(Collections.emptyList());

        Map<String, Object> summary = visualizationService.getSummaryMetrics();
        assertThat(summary.get("totalAnalyses")).isEqualTo(0L);
        assertThat(summary.get("avgConfidence")).isEqualTo(0.0);

        List<ProvinceDataDto> heatmap = visualizationService.getHeatmapData();
        assertThat(heatmap).hasSize(77); // All provinces with 0 value
        assertThat(heatmap.get(0).getValue()).isEqualTo(0);

        List<Map<String, Object>> trend = visualizationService.getTrendData(30, "day");
        assertThat(trend).isEmpty();
    }
}
