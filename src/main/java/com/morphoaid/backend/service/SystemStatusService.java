package com.morphoaid.backend.service;

import com.morphoaid.backend.entity.SystemSetting;
import com.morphoaid.backend.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class SystemStatusService {

    private final SystemSettingRepository systemSettingRepository;
    private static final String ULTRALYTICS_ENABLED_KEY = "ULTRALYTICS_ENABLED";

    @PostConstruct
    public void init() {
        if (!systemSettingRepository.existsById(ULTRALYTICS_ENABLED_KEY)) {
            systemSettingRepository.save(new SystemSetting(ULTRALYTICS_ENABLED_KEY, "true"));
        }
    }

    public boolean isUltralyticsEnabled() {
        SystemSetting setting = systemSettingRepository.findById(ULTRALYTICS_ENABLED_KEY).orElse(null);
        return setting == null || "true".equalsIgnoreCase(setting.getValue());
    }

    public boolean toggleUltralyticsStatus() {
        boolean currentState = isUltralyticsEnabled();
        boolean newState = !currentState;
        systemSettingRepository.save(new SystemSetting(ULTRALYTICS_ENABLED_KEY, String.valueOf(newState)));
        return newState;
    }
}
