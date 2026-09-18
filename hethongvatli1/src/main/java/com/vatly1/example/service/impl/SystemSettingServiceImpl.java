package com.vatly1.example.service.impl;

import com.vatly1.example.model.request.BulkUpdateSettingsRequest;
import com.vatly1.example.model.request.UpdateSettingRequest;
import com.vatly1.example.entity.SystemSetting;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.ISystemSettingRepository;
import com.vatly1.example.service.ISystemSettingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingServiceImpl implements ISystemSettingService {

    private final ISystemSettingRepository settingRepository;

    @PostConstruct
    public void initDefaults() {
        try {
            seedIfNotExists("exam.max_attempts", "3", "Số lần làm bài tối đa mỗi đề thi");
            seedIfNotExists("exam.late_penalty_percent", "0", "Trừ điểm khi nộp muộn (0 = không trừ)");
            seedIfNotExists("ai.daily_message_limit", "50", "Số tin nhắn AI tối đa mỗi SV mỗi ngày");
            seedIfNotExists("file.max_upload_mb", "50", "Kích thước file tối đa (MB)");
            seedIfNotExists("analytics.cron_expression", "\"0 0 1 * * *\"", "Biểu thức Cron cho Analytics job");
        } catch (Exception e) {
            log.warn("System settings default initialization note: {}", e.getMessage());
        }
    }

    private void seedIfNotExists(String key, String value, String desc) {
        if (settingRepository.findBySettingKey(key).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(key)
                    .settingValue(value)
                    .description(desc)
                    .updatedAt(Instant.now())
                    .build());
        }
    }

    @Override
    public List<SystemSetting> getAllSettings() {
        return settingRepository.findAll();
    }

    @Override
    public SystemSetting getSettingByKey(String key) {
        return settingRepository.findBySettingKey(key)
                .orElseThrow(() -> new CustomException("Setting not found with key: " + key, HttpStatus.NOT_FOUND));
    }

    @Override
    @Transactional
    public SystemSetting updateSetting(String key, UpdateSettingRequest request, UUID adminUserId) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElseGet(() -> SystemSetting.builder()
                        .settingKey(key)
                        .description(request.getDescription())
                        .build());

        if (request.getSettingValue() != null) {
            setting.setSettingValue(request.getSettingValue());
        }
        if (request.getDescription() != null) {
            setting.setDescription(request.getDescription());
        }
        setting.setUpdatedBy(adminUserId);
        setting.setUpdatedAt(Instant.now());
        return settingRepository.save(setting);
    }

    @Override
    @Transactional
    public List<SystemSetting> bulkUpdateSettings(BulkUpdateSettingsRequest request, UUID adminUserId) {
        List<SystemSetting> updatedList = new ArrayList<>();
        if (request == null || request.getSettings() == null) {
            return updatedList;
        }

        for (Map.Entry<String, String> entry : request.getSettings().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            SystemSetting setting = settingRepository.findBySettingKey(key)
                    .orElseGet(() -> SystemSetting.builder()
                            .settingKey(key)
                            .build());

            setting.setSettingValue(value);
            setting.setUpdatedBy(adminUserId);
            setting.setUpdatedAt(Instant.now());
            updatedList.add(settingRepository.save(setting));
        }

        return updatedList;
    }
}