package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.controller.VisualizationController;
import com.morphoaid.backend.service.VisualizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class VisualizationUtc07ControllerTest {

    @Mock
    private VisualizationService visualizationService;

    @InjectMocks
    private VisualizationController visualizationController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_07_TC_06_visualization_controllerSuccess() {
        // Summary
        when(visualizationService.getSummaryMetrics()).thenReturn(Map.of("total", 10));
        ResponseEntity<Map<String, Object>> summaryRes = visualizationController.getSummary();
        assertThat(summaryRes.getStatusCode().value()).isEqualTo(200);
        assertThat(summaryRes.getBody()).containsKey("total");

        // Heatmap
        when(visualizationService.getHeatmapData()).thenReturn(Collections.emptyList());
        ResponseEntity<?> heatmapRes = visualizationController.getHeatmapData();
        assertThat(heatmapRes.getStatusCode().value()).isEqualTo(200);

        // Trend
        when(visualizationService.getTrendData(7, "day")).thenReturn(List.of(Map.of("date", "2026-01-01")));
        ResponseEntity<List<Map<String, Object>>> trendRes = visualizationController.getTrendData(7, "day");
        assertThat(trendRes.getStatusCode().value()).isEqualTo(200);
        assertThat(trendRes.getBody().get(0)).containsKey("date");
    }
}
