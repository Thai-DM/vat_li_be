package com.vatly1.example.service;

import com.vatly1.example.model.dto.LearningProgressDTO;
import com.vatly1.example.model.dto.MaterialProgressDTO;
import com.vatly1.example.model.request.UpdateLearningProgressDTO;
import com.vatly1.example.model.request.UpdateMaterialProgressDTO;

import java.util.List;
import java.util.UUID;

public interface ILearningProgressService {
    List<LearningProgressDTO> getProgressByClass(UUID classId, UUID studentId, String role);
    List<LearningProgressDTO> getMyProgress(UUID studentId, UUID classId);
    LearningProgressDTO updateProgress(UUID studentId, UpdateLearningProgressDTO dto);
    MaterialProgressDTO updateMaterialProgress(UUID studentId, UpdateMaterialProgressDTO dto);
    List<MaterialProgressDTO> getMyMaterialProgress(UUID studentId, UUID classId, UUID topicId);
}