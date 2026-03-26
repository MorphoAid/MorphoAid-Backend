package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.dto.AIResultResponse;
import com.morphoaid.backend.entity.AIResult;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.repository.AIResultRepository;
import com.morphoaid.backend.repository.CaseImageRepository;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.CaseService;
import com.morphoaid.backend.service.ActivityService;
import com.morphoaid.backend.service.StorageService;
import com.morphoaid.backend.integration.ai.UltralyticsClient;
import com.morphoaid.backend.integration.ai.UltralyticsParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class CaseDetailUtc04AiResultTest {

    @Mock
    private AIResultRepository aiResultRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaseImageRepository caseImageRepository;

    @Mock
    private UltralyticsClient ultralyticsClient;

    @Mock
    private UltralyticsParser ultralyticsParser;

    @Mock
    private StorageService storageService;

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private CaseService caseService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_04_TC_04_findAiResultByCaseIdOrNull_success() {
        // UTC-04-TD-02
        Case aCase = Case.builder().id(50L).build();
        CaseImage image = CaseImage.builder().id(10L).caseEntity(aCase).build();
        
        AIResult aiResult = new AIResult();
        aiResult.setId(1L);
        aiResult.setCaseImage(image);
        aiResult.setConfidence(0.98);
        aiResult.setParasiteStage("Trophozoite");
        aiResult.setTopClassId(0);

        when(aiResultRepository.findByCaseImageCaseEntityId(50L)).thenReturn(Optional.of(aiResult));

        AIResultResponse response = caseService.findAiResultByCaseIdOrNull(50L);

        assertThat(response).isNotNull();
        assertThat(response.getConfidence()).isEqualTo(0.98);
        assertThat(response.getParasiteStage()).isEqualTo("Trophozoite");
        assertThat(response.getCaseId()).isEqualTo(50L);
    }

    @Test
    public void UTC_04_TC_04_findAiResultByCaseIdOrNull_null() {
        when(aiResultRepository.findByCaseImageCaseEntityId(999L)).thenReturn(Optional.empty());

        AIResultResponse response = caseService.findAiResultByCaseIdOrNull(999L);

        assertThat(response).isNull();
    }
}
