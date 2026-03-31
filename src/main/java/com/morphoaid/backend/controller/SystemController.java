package com.morphoaid.backend.controller;

import com.morphoaid.backend.dto.SystemStatusDto;
import com.morphoaid.backend.service.SystemStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemStatusService systemStatusService;

    @GetMapping("/status/ai")
    public ResponseEntity<SystemStatusDto> getAiStatus() {
        boolean isEnabled = systemStatusService.isUltralyticsEnabled();
        return ResponseEntity.ok(new SystemStatusDto(isEnabled ? "ONLINE" : "OFFLINE"));
    }

    @PostMapping("/status/ai/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemStatusDto> toggleAiStatus() {
        boolean newState = systemStatusService.toggleUltralyticsStatus();
        return ResponseEntity.ok(new SystemStatusDto(newState ? "ONLINE" : "OFFLINE"));
    }
}
