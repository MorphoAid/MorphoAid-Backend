package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.ProvinceDataDto;
import com.morphoaid.backend.service.VisualizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/visualization")
@RequiredArgsConstructor
public class VisualizationController {

    private final VisualizationService visualizationService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(visualizationService.getSummaryMetrics());
    }

    @GetMapping("/heatmap")
    public ResponseEntity<List<ProvinceDataDto>> getHeatmapData() {
        return ResponseEntity.ok(visualizationService.getHeatmapData());
    }

    @GetMapping("/trend")
    public ResponseEntity<List<Map<String, Object>>> getTrendData(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer days,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String groupBy) {
        return ResponseEntity.ok(visualizationService.getTrendData(days, groupBy));
    }

    @GetMapping("/ai-confidence")
    public ResponseEntity<List<Map<String, Object>>> getAiConfidenceData() {
        return ResponseEntity.ok(visualizationService.getAiConfidenceData());
    }
}
