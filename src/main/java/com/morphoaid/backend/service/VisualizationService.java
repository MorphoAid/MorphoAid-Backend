package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.ProvinceDataDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class VisualizationService {

    // A predefined list of some major and secondary provinces in Thailand for the
    // mock data
    private static final String[][] PROVINCES = {
            { "Bangkok", "กรุงเทพมหานคร" }, { "Chiang Mai", "เชียงใหม่" }, { "Phuket", "ภูเก็ต" },
            { "Chon Buri", "ชลบุรี" }, { "Khon Kaen", "ขอนแก่น" }, { "Nakhon Ratchasima", "นครราชสีมา" },
            { "Udon Thani", "อุดรธานี" }, { "Songkhla", "สงขลา" }, { "Surat Thani", "สุราษฎร์ธานี" },
            { "Nakhon Sawan", "นครสวรรค์" }, { "Ayutthaya", "พระนครศรีอยุธยา" }, { "Kanchanaburi", "กาญจนบุรี" },
            { "Rayong", "ระยอง" }, { "Ubon Ratchathani", "อุบลราชธานี" }, { "Chiang Rai", "เชียงราย" },
            { "Phitsanulok", "พิษณุโลก" }, { "Nakhon Pathom", "นครปฐม" }, { "Samut Prakan", "สมุทรปราการ" },
            { "Pathum Thani", "ปทุมธานี" }, { "Nonthaburi", "นนทบุรี" }, { "Samut Sakhon", "สมุทรสาคร" },
            { "Phetchaburi", "เพชรบุรี" }, { "Prachuap Khiri Khan", "ประจวบคีรีขันธ์" },
            { "Tak", "ตาก" }, { "Ratchaburi", "ราชบุรี" }
    };

    /**
     * Gets heatmap data for provinces.
     * Currently returns mock data. Designed to be swapped with a real database call
     * later.
     */
    public List<ProvinceDataDto> getHeatmapData() {
        List<ProvinceDataDto> dataList = new ArrayList<>();
        Random random = new Random();

        // Generate synthetic case numbers to represent a heatmap scale
        for (String[] province : PROVINCES) {
            // Random cases between 10 and 500 for demo purposes
            int mockCases = 10 + random.nextInt(490);

            dataList.add(ProvinceDataDto.builder()
                    .provinceNameEn(province[0])
                    .provinceNameTh(province[1])
                    .value(mockCases)
                    .build());
        }

        return dataList;
    }
}
