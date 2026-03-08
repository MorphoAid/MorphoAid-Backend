package com.morphoaid.backend.service;

import com.morphoaid.backend.constant.ProvinceConstant;
import com.morphoaid.backend.dto.ProvinceDataDto;
import com.morphoaid.backend.repository.CaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisualizationServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private VisualizationService visualizationService;

    // UTC-09-TC-01 Aggregate province or geographic data correctly from valid
    // dataset
    @Test
    void getHeatmapData_Success() {
        // Arrange
        // Raw case row format: [caseId (Long), provinceCode (String), provinceName
        // (String), location (String), parasiteStage (String)]
        List<Object[]> rawCases = new ArrayList<>();

        // Case 1: Has valid province code directly
        rawCases.add(new Object[] { 100L, "10", "Bangkok Metropolis", null, "RING" });

        // Case 2: Missing code but has valid province name that can be resolved
        rawCases.add(new Object[] { 101L, null, "Chiang Mai", null, "TROPH" });

        // Case 3: Same province as case 1, different case ID
        rawCases.add(new Object[] { 102L, "10", "Bangkok Metropolis", null, "SCHIZ" });

        // Case 4: Duplicate case row (e.g., multiple images for same case), should be
        // aggregated
        rawCases.add(new Object[] { 100L, "10", "Bangkok Metropolis", null, "RING" });

        // Case 5: Unresolvable code and name, should be skipped
        rawCases.add(new Object[] { 103L, null, "Unknown Realm", null, "RING" });

        when(caseRepository.findRawCasesForHeatmap()).thenReturn(rawCases);

        // Act
        List<ProvinceDataDto> result = visualizationService.getHeatmapData();

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Since the service always returns data for all 77 provinces based on
        // ProvinceConstant,
        // we verify that the specific ones we seeded have the correct aggregated
        // values.

        ProvinceDataDto bangkokData = result.stream()
                .filter(p -> "Bangkok Metropolis".equals(p.getProvinceNameEn()))
                .findFirst()
                .orElse(null);

        assertNotNull(bangkokData);
        assertEquals(2, bangkokData.getValue(), "Bangkok Metropolis should have 2 distinct cases (100 and 102)");
        assertNotNull(bangkokData.getCategories());
        assertTrue(bangkokData.getCategories().containsKey("RING") || bangkokData.getCategories().containsKey("SCHIZ"));

        ProvinceDataDto chiangMaiData = result.stream()
                .filter(p -> "Chiang Mai".equals(p.getProvinceNameEn()))
                .findFirst()
                .orElse(null);

        assertNotNull(chiangMaiData);
        assertEquals(1, chiangMaiData.getValue(), "Chiang Mai should have 1 distinct case (101)");
        assertEquals(1, chiangMaiData.getCategories().get("TROPH"));

        verify(caseRepository, times(1)).findRawCasesForHeatmap();
    }
}
