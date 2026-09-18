package com.vatly1.example.service;

import com.vatly1.example.model.request.CreateLearningMaterialDTO;
import com.vatly1.example.model.dto.LearningMaterialDTO;

import java.util.List;
import java.util.UUID;

public interface ILearningMaterialService {
    List<LearningMaterialDTO> getMaterialsByTopic(UUID topicId, String role);
    LearningMaterialDTO getMaterialById(UUID materialId, String role);
    LearningMaterialDTO createMaterial(CreateLearningMaterialDTO dto, UUID currentUserId);
    LearningMaterialDTO updateMaterial(UUID materialId, CreateLearningMaterialDTO dto, UUID currentUserId, String role);
    LearningMaterialDTO approveMaterial(UUID materialId);
    void deleteMaterial(UUID materialId, UUID currentUserId, String role);
}