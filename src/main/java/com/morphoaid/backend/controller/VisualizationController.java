package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.ProvinceDataDto;
import com.morphoaid.backend.service.VisualizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/visualization")
@RequiredArgsConstructor
public class VisualizationController {

    private final VisualizationService visualizationService;

    @GetMapping("/heatmap")
    public ResponseEntity<List<ProvinceDataDto>> getHeatmapData() {
        return ResponseEntity.ok(visualizationService.getHeatmapData());
    }
}
