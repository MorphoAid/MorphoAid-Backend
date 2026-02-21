package com.morphoaid.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morphoaid.backend.integration.ai.UltralyticsDetection;
import com.morphoaid.backend.integration.ai.UltralyticsParser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class UltralyticsParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final UltralyticsParser parser = new UltralyticsParser(mapper);

    @Test
    void testParseTopDetection_HighestConfidenceIsChosen() {
        // Mock JSON response with two bounding boxes (detections)
        // One is class 0 (conf: 0.8), another is class 4 (conf: 0.95)
        String json = """
                [
                  {
                     "names": { "0": "DrugA", "1": "DrugB", "2": "RING", "3": "SCHIZ", "4": "TROPH" },
                     "boxes": [
                         { "class": 0, "conf": 0.8, "box": {"x1": 10, "y1": 10, "x2": 20, "y2": 20} },
                         { "class": 4, "conf": 0.95, "box": {"x1": 30, "y1": 30, "x2": 40, "y2": 40} }
                     ]
                  }
                ]
                """;

        Optional<UltralyticsDetection> detection = parser.parseTopDetection(json);

        assertTrue(detection.isPresent());
        UltralyticsDetection res = detection.get();

        // Highest confidence should be 0.95
        assertEquals(0.95, res.confidence());
        assertEquals(4, res.topClassId());

        // Mapped rules for class 4 -> drugExposure=false, parasiteStage="TROPH"
        assertFalse(res.drugExposure());
        assertNull(res.drugType());
        assertEquals("TROPH", res.parasiteStage());
    }

    @Test
    void testParseTopDetection_Class0Mappings() {
        String json = """
                [
                  {
                     "boxes": [
                         { "class": 0, "conf": 0.99, "box": {} }
                     ]
                  }
                ]
                """;

        Optional<UltralyticsDetection> detection = parser.parseTopDetection(json);
        assertTrue(detection.isPresent());

        // Mapped rules for class 0 -> drugExposure=true, drugType="A"
        assertTrue(detection.get().drugExposure());
        assertEquals("A", detection.get().drugType());
        assertNull(detection.get().parasiteStage());
    }

    @Test
    void testParseTopDetection_NoBoxes() {
        String json = """
                [
                  {
                     "boxes": []
                  }
                ]
                """;
        Optional<UltralyticsDetection> detection = parser.parseTopDetection(json);
        assertFalse(detection.isPresent());
    }
}
