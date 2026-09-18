package com.vatly1.example.service;

import com.vatly1.example.model.request.BulkUpdateSettingsRequest;
import com.vatly1.example.model.request.UpdateSettingRequest;
import com.vatly1.example.entity.SystemSetting;

import java.util.List;
import java.util.UUID;

public interface ISystemSettingService {
    List<SystemSetting> getAllSettings();
    SystemSetting getSettingByKey(String key);
    SystemSetting updateSetting(String key, UpdateSettingRequest request, UUID adminUserId);
    List<SystemSetting> bulkUpdateSettings(BulkUpdateSettingsRequest request, UUID adminUserId);
}