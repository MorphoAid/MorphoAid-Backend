package com.morphoaid.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDataDto {
    private String provinceNameEn;
    private String provinceNameTh;
    private int value; // Total cases count
    private Map<String, Integer> categories; // Counts by parasite stage
}
