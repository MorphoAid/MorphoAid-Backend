package com.morphoaid.backend.controller;

import com.morphoaid.backend.entity.Activity;
import com.morphoaid.backend.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/admin/activities")
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Activity>> getRecentActivities() {
        return ResponseEntity.ok(activityService.getRecentActivities());
    }
}
