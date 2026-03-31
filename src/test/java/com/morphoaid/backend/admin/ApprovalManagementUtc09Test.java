package com.morphoaid.backend.admin;

import com.morphoaid.backend.controller.AdminUserController;
import com.morphoaid.backend.dto.AdminUserResponse;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.ActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ApprovalManagementUtc09Test {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private AdminUserController adminUserController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void UTC_09_TC_01_getPendingUsers_list() {
        // UTC-09-TD-01
        User pending = User.builder().id(50L).email("pending@test.com").approved(false).build();
        when(userRepository.findByApproved(false)).thenReturn(List.of(pending));

        ResponseEntity<List<AdminUserResponse>> response = adminUserController.getPendingUsers();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getEmail()).isEqualTo("pending@test.com");
    }

    @Test
    public void UTC_09_TC_02_approveUser_success() {
        // UTC-09-TD-02
        User user = User.builder().id(50L).email("pending@test.com").approved(false).build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));

        ResponseEntity<?> response = adminUserController.approveUser(50L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(user.isApproved()).isTrue();
        verify(userRepository, times(1)).save(user);
        verify(activityService, times(1)).log(anyString(), eq(Role.ADMIN), anyString(), anyString(), eq("Success"));
    }

    @Test
    public void UTC_09_TC_03_rejectUser_success() {
        // UTC-09-TD-02 flow
        when(userRepository.existsById(50L)).thenReturn(true);

        ResponseEntity<?> response = adminUserController.rejectUser(50L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(userRepository, times(1)).deleteById(50L);
        verify(activityService, times(1)).log(anyString(), eq(Role.ADMIN), anyString(), anyString(), eq("Success"));
    }

    @Test
    public void UTC_09_TC_04_approveUser_notFound() {
        // UTC-09-TD-03
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = adminUserController.approveUser(999L);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
