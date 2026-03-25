package com.morphoaid.backend.service;

import com.morphoaid.backend.entity.Activity;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;

    public void log(String email, Role role, String action, String target, String status) {
        Activity activity = Activity.builder()
                .timestamp(LocalDateTime.now())
                .userEmail(email)
                .userRole(role != null ? role.name() : "SYSTEM")
                .action(action)
                .target(target)
                .status(status)
                .build();
        activityRepository.save(activity);
    }

    public List<Activity> getRecentActivities() {
        return activityRepository.findTop50ByOrderByTimestampDesc();
    }
}
