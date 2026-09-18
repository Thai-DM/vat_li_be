package com.vatly1.example.service.impl;

import com.vatly1.example.dto.dto.AiTopicGapDTO;
import com.vatly1.example.dto.dto.MaterialEffectivenessDTO;
import com.vatly1.example.dto.dto.QuestionQualityDTO;
import com.vatly1.example.dto.dto.TopicDifficultyDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.AiTopicGapStatRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.MaterialEffectivenessStatRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionStatRepository;
import com.vatly1.example.repository.TopicDifficultyStatRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements IAnalyticsService {

    private final TopicDifficultyStatRepository topicDifficultyStatRepository;
    private final QuestionStatRepository questionStatRepository;
    private final AiTopicGapStatRepository aiTopicGapStatRepository;
    private final MaterialEffectivenessStatRepository materialEffectivenessStatRepository;
    private final IClassRepository classRepository;
    private final IClassStaffRepository classStaffRepository;
    private final TopicRepository topicRepository;
    private final QuestionBankRepository questionBankRepository;
    private final LearningMaterialRepository learningMaterialRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TopicDifficultyDTO> getTopicDifficulty(UUID classId, UUID subjectId, String period, UUID requesterId, String role) {
        if (classId != null) {
            validateClassAccess(classId, requesterId, role);
        }

        var stats = topicDifficultyStatRepository.filterStats(classId, subjectId, period);
        if (stats.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, String> topicNameMap = topicRepository.findAll().stream()
                .collect(Collectors.toMap(Topic::getTopicId, Topic::getTopicName, (a, b) -> a));

        return stats.stream().map(s -> TopicDifficultyDTO.builder()
                .statId(s.getStatId())
                .subjectId(s.getSubjectId())
                .topicId(s.getTopicId())
                .topicName(topicNameMap.getOrDefault(s.getTopicId(), "Chưa phân loại"))
                .classId(s.getClassId())
                .avgScore(s.getAvgScore())
                .errorRate(s.getErrorRate())
                .commonWrongOptionsJson(s.getCommonWrongOptionsJson())
                .period(s.getPeriod())
                .generatedAt(s.getGeneratedAt())
                .build()
        ).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionQualityDTO> getQuestionQuality(UUID subjectId, UUID topicId, Integer minUsed) {
        var stats = questionStatRepository.filterStats(subjectId, topicId, minUsed);
        if (stats.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, QuestionBank> questionMap = questionBankRepository.findAll().stream()
                .collect(Collectors.toMap(QuestionBank::getQuestionId, q -> q, (a, b) -> a));

        Map<UUID, String> topicNameMap = topicRepository.findAll().stream()
                .collect(Collectors.toMap(Topic::getTopicId, Topic::getTopicName, (a, b) -> a));

        return stats.stream().map(qs -> {
            QuestionBank q = questionMap.get(qs.getQuestionId());
            String text = q != null ? q.getContent() : null;
            UUID tId = q != null ? q.getTopicId() : null;
            String tName = tId != null ? topicNameMap.getOrDefault(tId, "Chưa phân loại") : null;
            String label = computeQualityLabel(qs.getDiscriminationIndex());

            return QuestionQualityDTO.builder()
                    .questionId(qs.getQuestionId())
                    .questionText(text)
                    .topicId(tId)
                    .topicName(tName)
                    .timesUsed(qs.getTimesUsed())
                    .correctRate(qs.getCorrectRate())
                    .discriminationIndex(qs.getDiscriminationIndex())
                    .avgTimeSeconds(qs.getAvgTimeSeconds())
                    .qualityLabel(label)
                    .updatedAt(qs.getUpdatedAt())
                    .build();
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiTopicGapDTO> getAiGaps(UUID subjectId, String period) {
        var stats = aiTopicGapStatRepository.filterStats(subjectId, period);
        if (stats.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, String> topicNameMap = topicRepository.findAll().stream()
                .collect(Collectors.toMap(Topic::getTopicId, Topic::getTopicName, (a, b) -> a));

        return stats.stream().map(g -> AiTopicGapDTO.builder()
                .gapId(g.getGapId())
                .subjectId(g.getSubjectId())
                .topicId(g.getTopicId())
                .topicName(topicNameMap.getOrDefault(g.getTopicId(), "Chưa phân loại"))
                .refusalCount(g.getRefusalCount())
                .frequentQuerySample(g.getFrequentQuerySample())
                .period(g.getPeriod())
                .generatedAt(g.getGeneratedAt())
                .build()
        ).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterialEffectivenessDTO> getMaterialEffectiveness(UUID subjectId, UUID topicId, String period) {
        var stats = materialEffectivenessStatRepository.filterStats(subjectId, topicId, period);
        if (stats.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, LearningMaterial> matMap = learningMaterialRepository.findAll().stream()
                .collect(Collectors.toMap(LearningMaterial::getMaterialId, m -> m, (a, b) -> a));

        Map<UUID, String> topicNameMap = topicRepository.findAll().stream()
                .collect(Collectors.toMap(Topic::getTopicId, Topic::getTopicName, (a, b) -> a));

        return stats.stream().map(m -> {
            LearningMaterial lm = matMap.get(m.getMaterialId());
            String title = lm != null ? lm.getTitle() : null;
            UUID tId = lm != null ? lm.getTopicId() : null;
            String tName = tId != null ? topicNameMap.getOrDefault(tId, "Chưa phân loại") : null;

            return MaterialEffectivenessDTO.builder()
                    .materialId(m.getMaterialId())
                    .title(title)
                    .topicId(tId)
                    .topicName(tName)
                    .period(m.getPeriod())
                    .viewCount(m.getViewCount())
                    .avgTimeSpentSeconds(m.getAvgTimeSpentSeconds())
                    .correlatedScoreImprovement(m.getCorrelatedScoreImprovement())
                    .build();
        }).toList();
    }

    private void validateClassAccess(UUID classId, UUID requesterId, String role) {
        if (role != null && role.contains("ADMIN")) {
            return;
        }

        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Lớp học không tồn tại", HttpStatus.NOT_FOUND));

        if (role != null && role.contains("INSTRUCTOR")) {
            boolean isInstructor = clazz.getInstructorId() != null && clazz.getInstructorId().equals(requesterId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(classId, requesterId);
            if (!isInstructor && !isStaff) {
                throw new CustomException("Bạn không phụ trách lớp này", HttpStatus.FORBIDDEN);
            }
            return;
        }

        throw new CustomException("Bạn không có quyền truy cập lớp học này", HttpStatus.FORBIDDEN);
    }

    private String computeQualityLabel(BigDecimal di) {
        if (di == null) {
            return "UNEVALUATED";
        }
        double val = di.doubleValue();
        if (val >= 0.35) {
            return "EXCELLENT";
        } else if (val >= 0.20) {
            return "GOOD";
        } else if (val >= 0.10) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }
}