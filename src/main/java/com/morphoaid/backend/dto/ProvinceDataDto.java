package com.morphoaid.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDataDto {
    private String provinceNameEn;
    private String provinceNameTh;
    private int value; // Mockup cases count
}
