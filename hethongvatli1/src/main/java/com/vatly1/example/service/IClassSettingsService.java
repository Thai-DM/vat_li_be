package com.vatly1.example.service;

import com.vatly1.example.entity.ClassSettings;
import com.vatly1.example.model.dto.ClassSettingsDTO;
import com.vatly1.example.model.request.UpdateClassSettingsDTO;

import java.util.UUID;

public interface IClassSettingsService {
    ClassSettingsDTO getClassSettings(UUID classId, UUID currentUserId, String currentUserRole);
    ClassSettingsDTO updateClassSettings(UUID classId, UpdateClassSettingsDTO dto, UUID currentUserId, String currentUserRole);
    ClassSettings getEffectiveSettings(UUID classId);
}
