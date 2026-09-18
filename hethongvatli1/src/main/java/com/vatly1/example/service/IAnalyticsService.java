package com.vatly1.example.service;

import com.vatly1.example.dto.AiTopicGapDTO;
import com.vatly1.example.dto.MaterialEffectivenessDTO;
import com.vatly1.example.dto.QuestionQualityDTO;
import com.vatly1.example.dto.TopicDifficultyDTO;

import java.util.List;
import java.util.UUID;

public interface IAnalyticsService {
    List<TopicDifficultyDTO> getTopicDifficulty(UUID classId, UUID subjectId, String period, UUID requesterId, String role);
    List<QuestionQualityDTO> getQuestionQuality(UUID subjectId, UUID topicId, Integer minUsed);
    List<AiTopicGapDTO> getAiGaps(UUID subjectId, String period);
    List<MaterialEffectivenessDTO> getMaterialEffectiveness(UUID subjectId, UUID topicId, String period);
}
