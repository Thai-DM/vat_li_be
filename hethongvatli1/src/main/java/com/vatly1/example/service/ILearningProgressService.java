package com.vatly1.example.service;

import com.vatly1.example.dto.LearningProgressDTO;
import com.vatly1.example.dto.UpdateLearningProgressDTO;

import java.util.List;
import java.util.UUID;

public interface ILearningProgressService {
    List<LearningProgressDTO> getProgressByClass(UUID classId, UUID studentId, String role);
    List<LearningProgressDTO> getMyProgress(UUID studentId, UUID classId);
    LearningProgressDTO updateProgress(UUID studentId, UpdateLearningProgressDTO dto);
}
