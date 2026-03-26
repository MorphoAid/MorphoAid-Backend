package com.morphoaid.backend.caseunit;

import com.morphoaid.backend.dto.CaseResponse;
import com.morphoaid.backend.entity.Case;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.CaseRepository;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.CaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class CaseListUtc03ServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.morphoaid.backend.repository.CaseImageRepository caseImageRepository;

    @Mock
    private com.morphoaid.backend.repository.AIResultRepository aiResultRepository;

    @Mock
    private com.morphoaid.backend.integration.ai.UltralyticsClient ultralyticsClient;

    @Mock
    private com.morphoaid.backend.integration.ai.UltralyticsParser ultralyticsParser;

    @Mock
    private com.morphoaid.backend.service.StorageService storageService;

    @Mock
    private com.morphoaid.backend.service.ActivityService activityService;

    @InjectMocks
    private CaseService caseService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_03_TC_01_getCases_dataUseRole() {
        // UTC-03-TD-01
        User user = User.builder().id(100L).email("doctor@test.com").role(Role.DATA_USE).build();
        when(userRepository.findByEmail("doctor@test.com")).thenReturn(Optional.of(user));

        // UTC-03-TD-03
        Case case1 = Case.builder().id(11L).uploadedBy(user).build();
        Case case2 = Case.builder().id(10L).uploadedBy(user).build();
        when(caseRepository.findAllByUploadedByOrderByIdDesc(user)).thenReturn(List.of(case1, case2));

        List<CaseResponse> results = caseService.getCases("doctor@test.com");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUploadedById()).isEqualTo(100L);
        assertThat(results.get(1).getUploadedById()).isEqualTo(100L);
    }

    @Test
    public void UTC_03_TC_02_getCases_adminRole() {
        // UTC-03-TD-02
        User admin = User.builder().id(1L).email("admin@test.com").role(Role.ADMIN).build();
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        // UTC-03-TD-04
        Case case1 = Case.builder().id(1L).build();
        Case case2 = Case.builder().id(2L).build();
        Case case10 = Case.builder().id(10L).build();
        Case case11 = Case.builder().id(11L).build();
        
        // Repo returns in Desc order
        when(caseRepository.findAllByOrderByIdDesc()).thenReturn(List.of(case11, case10, case2, case1));

        List<CaseResponse> results = caseService.getCases("admin@test.com");

        assertThat(results).hasSize(4);
        assertThat(results.get(0).getId()).isEqualTo(11L);
        assertThat(results.get(3).getId()).isEqualTo(1L);
    }

    @Test
    public void UTC_03_TC_03_getCases_emptyList() {
        // UTC-03-TD-05
        User user = User.builder().id(100L).email("doctor@test.com").role(Role.DATA_USE).build();
        when(userRepository.findByEmail("doctor@test.com")).thenReturn(Optional.of(user));
        when(caseRepository.findAllByUploadedByOrderByIdDesc(any())).thenReturn(Collections.emptyList());

        List<CaseResponse> results = caseService.getCases("doctor@test.com");

        assertThat(results).isEmpty();
    }

    @Test
    public void UTC_03_TC_05_toCaseResponse_mappingCheck() {
        Case entity = Case.builder()
                .id(99L)
                .patientCode(555L)
                .technicianId("TECH-01")
                .location("Bangkok")
                .provinceName("Bangkok")
                .provinceCode("10")
                .status(com.morphoaid.backend.entity.CaseStatus.ANALYZED)
                .analysisStatus(com.morphoaid.backend.entity.AnalysisStatus.COMPLETED)
                .uploadedBy(User.builder().id(100L).firstName("John").build())
                .build();

        // toCaseResponse is private in CaseService, so we test it via getCases or getCaseById
        // Or we use Reflection, but testing it via public getCases is cleaner.
        User user = User.builder().id(100L).email("doctor@test.com").role(Role.DATA_USE).build();
        when(userRepository.findByEmail("doctor@test.com")).thenReturn(Optional.of(user));
        when(caseRepository.findAllByUploadedByOrderByIdDesc(user)).thenReturn(List.of(entity));

        List<CaseResponse> results = caseService.getCases("doctor@test.com");
        CaseResponse dto = results.get(0);

        assertThat(dto.getId()).isEqualTo(99L);
        assertThat(dto.getPatientCode()).isEqualTo(555L);
        assertThat(dto.getTechnicianId()).isEqualTo("TECH-01");
        assertThat(dto.getProvinceName()).isEqualTo("Bangkok");
        assertThat(dto.getStatus()).isEqualTo("ANALYZED");
        assertThat(dto.getAnalysisStatus()).isEqualTo("COMPLETED");
        assertThat(dto.getUploadedBy().getFirstName()).isEqualTo("John");
    }

    @Test
    public void UTC_03_TC_06_getCases_sortOrderCheck() {
        User user = User.builder().id(100L).email("doctor@test.com").role(Role.DATA_USE).build();
        when(userRepository.findByEmail("doctor@test.com")).thenReturn(Optional.of(user));

        Case caseOld = Case.builder().id(50L).build();
        Case caseNew = Case.builder().id(100L).build();
        
        // Mock repo to return in descending order as per method name contract
        when(caseRepository.findAllByUploadedByOrderByIdDesc(user)).thenReturn(List.of(caseNew, caseOld));

        List<CaseResponse> results = caseService.getCases("doctor@test.com");

        assertThat(results.get(0).getId()).isEqualTo(100L);
        assertThat(results.get(1).getId()).isEqualTo(50L);
    }
}
