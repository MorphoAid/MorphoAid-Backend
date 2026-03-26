package com.morphoaid.backend.admin;

import com.morphoaid.backend.controller.AdminActivityController;
import com.morphoaid.backend.entity.Activity;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.repository.ActivityRepository;
import com.morphoaid.backend.service.ActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ActivityLogUtc10Test {

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ActivityService activityService;

    @Mock // Mock the service for controller test
    private ActivityService activityServiceMock;

    @InjectMocks
    private AdminActivityController adminActivityController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_10_TC_01_activityLog_success() {
        // UTC-10-TD-01
        activityService.log("admin@test.com", Role.ADMIN, "Delete", "User X", "Success");

        verify(activityRepository, times(1)).save(any(Activity.class));
    }

    @Test
    public void UTC_10_TC_02_getRecentActivities_success() {
        // UTC-10-TD-02
        Activity activity = Activity.builder().action("Log").build();
        when(activityServiceMock.getRecentActivities()).thenReturn(List.of(activity));

        ResponseEntity<List<Activity>> response = adminActivityController.getRecentActivities();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    public void UTC_10_TC_03_getRecentActivities_empty() {
        when(activityServiceMock.getRecentActivities()).thenReturn(Collections.emptyList());

        ResponseEntity<List<Activity>> response = adminActivityController.getRecentActivities();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }
}
