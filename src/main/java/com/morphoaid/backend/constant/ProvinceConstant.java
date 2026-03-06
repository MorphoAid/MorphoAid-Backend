package com.morphoaid.backend.constant;

import java.util.HashMap;
import java.util.Map;

public class ProvinceConstant {

    public static final String[][] PROVINCES = {
            { "Amnat Charoen", "อำนาจเจริญ", "37" }, { "Ang Thong", "อ่างทอง", "15" },
            { "Bangkok", "กรุงเทพมหานคร", "10" },
            { "Bueng Kan", "บึงกาฬ", "38" }, { "Buri Ram", "บุรีรัมย์", "31" }, { "Chachoengsao", "ฉะเชิงเทรา", "24" },
            { "Chai Nat", "ชัยนาท", "18" }, { "Chaiyaphum", "ชัยภูมิ", "36" }, { "Chanthaburi", "จันทบุรี", "22" },
            { "Chiang Mai", "เชียงใหม่", "50" }, { "Chiang Rai", "เชียงราย", "57" }, { "Chon Buri", "ชลบุรี", "20" },
            { "Chumphon", "ชุมพร", "86" }, { "Kalasin", "กาฬสินธุ์", "46" }, { "Kamphaeng Phet", "กำแพงเพชร", "62" },
            { "Kanchanaburi", "กาญจนบุรี", "71" }, { "Khon Kaen", "ขอนแก่น", "40" }, { "Krabi", "กระบี่", "81" },
            { "Lampang", "ลำปาง", "52" }, { "Lamphun", "ลำพูน", "51" }, { "Loei", "เลย", "42" },
            { "Lop Buri", "ลพบุรี", "16" }, { "Mae Hong Son", "แม่ฮ่องสอน", "58" },
            { "Maha Sarakham", "มหาสารคาม", "44" },
            { "Mukdahan", "มุกดาหาร", "49" }, { "Nakhon Nayok", "นครนายก", "26" }, { "Nakhon Pathom", "นครปฐม", "73" },
            { "Nakhon Phanom", "นครพนม", "48" }, { "Nakhon Ratchasima", "นครราชสีมา", "30" },
            { "Nakhon Sawan", "นครสวรรค์", "60" },
            { "Nakhon Si Thammarat", "นครศรีธรรมราช", "80" }, { "Nan", "น่าน", "55" },
            { "Narathiwat", "นราธิวาส", "96" },
            { "Nong Bua Lam Phu", "หนองบัวลำภู", "39" }, { "Nong Khai", "หนองคาย", "43" },
            { "Nonthaburi", "นนทบุรี", "12" },
            { "Pathum Thani", "ปทุมธานี", "13" }, { "Pattani", "ปัตตานี", "94" }, { "Phangnga", "พังงา", "82" },
            { "Phatthalung", "พัทลุง", "93" }, { "Phayao", "พะเยา", "56" }, { "Phetchabun", "เพชรบูรณ์", "67" },
            { "Phetchaburi", "เพชรบุรี", "76" }, { "Phichit", "พิจิตร", "66" }, { "Phitsanulok", "พิษณุโลก", "65" },
            { "Phra Nakhon Si Ayutthaya", "พระนครศรีอยุธยา", "14" }, { "Phrae", "แพร่", "54" },
            { "Phuket", "ภูเก็ต", "83" },
            { "Prachin Buri", "ปราจีนบุรี", "25" }, { "Prachuap Khiri Khan", "ประจวบคีรีขันธ์", "77" },
            { "Ranong", "ระนอง", "85" },
            { "Ratchaburi", "ราชบุรี", "70" }, { "Rayong", "ระยอง", "21" }, { "Roi Et", "ร้อยเอ็ด", "45" },
            { "Sa Kaeo", "สระแก้ว", "27" }, { "Sakon Nakhon", "สกลนคร", "47" }, { "Samut Prakan", "สมุทรปราการ", "11" },
            { "Samut Sakhon", "สมุทรสาคร", "74" }, { "Samut Songkhram", "สมุทรสงคราม", "75" },
            { "Saraburi", "สระบุรี", "19" },
            { "Satun", "สตูล", "91" }, { "Sing Buri", "สิงห์บุรี", "17" }, { "Si Sa Ket", "ศรีสะเกษ", "33" },
            { "Songkhla", "สงขลา", "90" }, { "Sukhothai", "สุโขทัย", "64" }, { "Suphan Buri", "สุพรรณบุรี", "72" },
            { "Surat Thani", "สุราษฎร์ธานี", "84" }, { "Surin", "สุรินทร์", "32" }, { "Tak", "ตาก", "63" },
            { "Trang", "ตรัง", "92" }, { "Trat", "ตราด", "23" }, { "Ubon Ratchathani", "อุบลราชธานี", "34" },
            { "Udon Thani", "อุดรธานี", "41" }, { "Uthai Thani", "อุทัยธานี", "61" },
            { "Uttaradit", "อุตรดิตถ์", "53" },
            { "Yala", "ยะลา", "95" }, { "Yasothon", "ยโสธร", "35" }
    };

    private static final Map<String, String> THAI_TO_CODE_MAP = new HashMap<>();
    private static final Map<String, String> ENGLISH_TO_CODE_MAP = new HashMap<>();
    private static final Map<String, String> CODE_TO_THAI_MAP = new HashMap<>();
    private static final Map<String, String> CODE_TO_ENGLISH_MAP = new HashMap<>();

    static {
        for (String[] province : PROVINCES) {
            String enName = province[0].trim().toLowerCase();
            String thName = province[1].trim();
            String code = province[2].trim();

            THAI_TO_CODE_MAP.put(thName, code);
            ENGLISH_TO_CODE_MAP.put(enName, code);
            CODE_TO_THAI_MAP.put(code, province[1].trim());
            CODE_TO_ENGLISH_MAP.put(code, province[0].trim());
        }
    }

    public static String getProvinceCode(String provinceName) {
        if (provinceName == null || provinceName.trim().isEmpty()) {
            return null;
        }

        String trimmed = provinceName.trim();
        String code = THAI_TO_CODE_MAP.get(trimmed);
        if (code != null) {
            return code;
        }

        return ENGLISH_TO_CODE_MAP.get(trimmed.toLowerCase());
    }

    public static String getThaiNameByCode(String code) {
        if (code == null)
            return null;
        return CODE_TO_THAI_MAP.get(code.trim());
    }

    public static String getEnglishNameByCode(String code) {
        if (code == null)
            return null;
        return CODE_TO_ENGLISH_MAP.get(code.trim());
    }
}
