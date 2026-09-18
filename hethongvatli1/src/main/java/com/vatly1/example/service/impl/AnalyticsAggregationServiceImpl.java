package com.vatly1.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vatly1.example.entity.ActivityLog;
import com.vatly1.example.entity.AiConversation;
import com.vatly1.example.entity.AiMessage;
import com.vatly1.example.entity.AiRefusal;
import com.vatly1.example.entity.AiTopicGapStat;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAnswer;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.MaterialEffectivenessStat;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionStat;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.TopicDifficultyStat;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.repository.ActivityLogRepository;
import com.vatly1.example.repository.AiRefusalRepository;
import com.vatly1.example.repository.AiTopicGapStatRepository;
import com.vatly1.example.repository.IAiConversationRepository;
import com.vatly1.example.repository.IAiMessageRepository;
import com.vatly1.example.repository.IExamAnswerRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.MaterialEffectivenessStatRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionStatRepository;
import com.vatly1.example.repository.TopicDifficultyStatRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IAnalyticsAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsAggregationServiceImpl implements IAnalyticsAggregationService {

    private final TopicDifficultyStatRepository topicDifficultyStatRepository;
    private final QuestionStatRepository questionStatRepository;
    private final AiTopicGapStatRepository aiTopicGapStatRepository;
    private final MaterialEffectivenessStatRepository materialEffectivenessStatRepository;
    private final IExamRepository examRepository;
    private final IExamAttemptRepository examAttemptRepository;
    private final IExamAnswerRepository examAnswerRepository;
    private final QuestionBankRepository questionBankRepository;
    private final TopicRepository topicRepository;
    private final LearningMaterialRepository learningMaterialRepository;
    private final ActivityLogRepository activityLogRepository;
    private final AiRefusalRepository aiRefusalRepository;
    private final IAiMessageRepository aiMessageRepository;
    private final IAiConversationRepository aiConversationRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void aggregateTopicDifficulty(String period) {
        log.info("Aggregating topic difficulty for period: {}", period);

        // 1. Get all exams
        List<Exam> exams = examRepository.findAll();
        if (exams.isEmpty()) {
            return;
        }

        Map<UUID, Exam> examMap = exams.stream()
                .collect(Collectors.toMap(Exam::getExamId, e -> e, (a, b) -> a));

        // 2. Get submitted attempts
        List<ExamAttempt> attempts = examAttemptRepository.findAll().stream()
                .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED)
                .filter(a -> matchesPeriod(a.getSubmittedAt(), period))
                .toList();

        if (attempts.isEmpty()) {
            return;
        }

        // Map attemptId -> Exam
        Map<UUID, Exam> attemptExamMap = new HashMap<>();
        for (ExamAttempt att : attempts) {
            Exam ex = examMap.get(att.getExamId());
            if (ex != null && ex.getClassId() != null) {
                attemptExamMap.put(att.getAttemptId(), ex);
            }
        }

        // 3. Question metadata map
        Map<UUID, QuestionBank> questionMap = questionBankRepository.findAll().stream()
                .collect(Collectors.toMap(QuestionBank::getQuestionId, q -> q, (a, b) -> a));

        // 4. Answers grouped by (classId, topicId)
        // Key: classId -> (topicId -> list of answers)
        Map<UUID, Map<UUID, List<ExamAnswer>>> classTopicAnswers = new HashMap<>();

        for (ExamAttempt att : attempts) {
            Exam ex = attemptExamMap.get(att.getAttemptId());
            if (ex == null) continue;
            UUID classId = ex.getClassId();

            List<ExamAnswer> answers = examAnswerRepository.findByAttemptId(att.getAttemptId());
            for (ExamAnswer ans : answers) {
                QuestionBank q = questionMap.get(ans.getQuestionId());
                if (q == null || q.getTopicId() == null) continue;
                UUID topicId = q.getTopicId();

                classTopicAnswers
                        .computeIfAbsent(classId, k -> new HashMap<>())
                        .computeIfAbsent(topicId, k -> new ArrayList<>())
                        .add(ans);
            }
        }

        // 5. Compute stats and save
        List<TopicDifficultyStat> statsToSave = new ArrayList<>();

        for (Map.Entry<UUID, Map<UUID, List<ExamAnswer>>> classEntry : classTopicAnswers.entrySet()) {
            UUID classId = classEntry.getKey();

            // Delete old stats for this class & period to ensure idempotent upsert
            topicDifficultyStatRepository.deleteByClassIdAndPeriod(classId, period);

            for (Map.Entry<UUID, List<ExamAnswer>> topicEntry : classEntry.getValue().entrySet()) {
                UUID topicId = topicEntry.getKey();
                List<ExamAnswer> topicAnswers = topicEntry.getValue();
                if (topicAnswers.isEmpty()) continue;

                int totalCount = topicAnswers.size();
                long correctCount = topicAnswers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
                long wrongCount = totalCount - correctCount;

                BigDecimal avgScore = BigDecimal.valueOf((correctCount * 100.0) / totalCount)
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal errorRate = BigDecimal.valueOf((wrongCount * 1.0) / totalCount)
                        .setScale(4, RoundingMode.HALF_UP);

                // Option wrong frequency
                Map<UUID, Integer> wrongOptionCount = new HashMap<>();
                for (ExamAnswer ans : topicAnswers) {
                    if (Boolean.FALSE.equals(ans.getIsCorrect()) && ans.getSelectedOptionIds() != null) {
                        for (UUID optId : ans.getSelectedOptionIds()) {
                            wrongOptionCount.put(optId, wrongOptionCount.getOrDefault(optId, 0) + 1);
                        }
                    }
                }

                ArrayNode wrongOptionsArray = objectMapper.createArrayNode();
                wrongOptionCount.entrySet().stream()
                        .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                        .limit(5)
                        .forEach(entry -> {
                            ObjectNode node = objectMapper.createObjectNode();
                            node.put("optionId", entry.getKey().toString());
                            node.put("count", entry.getValue());
                            wrongOptionsArray.add(node);
                        });

                // Get subjectId from question
                UUID subjectId = topicAnswers.stream()
                        .map(a -> questionMap.get(a.getQuestionId()))
                        .filter(Objects::nonNull)
                        .map(QuestionBank::getSubjectId)
                        .filter(Objects::nonNull)
                        .findFirst().orElse(null);

                TopicDifficultyStat stat = TopicDifficultyStat.builder()
                        .classId(classId)
                        .subjectId(subjectId)
                        .topicId(topicId)
                        .avgScore(avgScore)
                        .errorRate(errorRate)
                        .commonWrongOptionsJson(wrongOptionsArray)
                        .period(period)
                        .generatedAt(Instant.now())
                        .build();

                statsToSave.add(stat);
            }
        }

        topicDifficultyStatRepository.saveAll(statsToSave);
        log.info("Saved {} topic difficulty stats for period {}", statsToSave.size(), period);
    }

    @Override
    @Transactional
    public void aggregateQuestionStats(String period) {
        log.info("Aggregating question stats for period: {}", period);

        List<QuestionBank> allQuestions = questionBankRepository.findAll();
        if (allQuestions.isEmpty()) {
            return;
        }

        List<ExamAttempt> attempts = examAttemptRepository.findAll().stream()
                .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED)
                .filter(a -> matchesPeriod(a.getSubmittedAt(), period))
                .toList();

        Map<UUID, ExamAttempt> attemptMap = attempts.stream()
                .collect(Collectors.toMap(ExamAttempt::getAttemptId, a -> a, (a, b) -> a));

        List<QuestionStat> stats = new ArrayList<>();

        for (QuestionBank q : allQuestions) {
            List<ExamAnswer> answers = examAnswerRepository.findByQuestionId(q.getQuestionId()).stream()
                    .filter(ans -> attemptMap.containsKey(ans.getAttemptId()))
                    .toList();

            int timesUsed = answers.size();
            BigDecimal correctRate = BigDecimal.ZERO;
            BigDecimal di = null;

            if (timesUsed > 0) {
                long correctCount = answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
                correctRate = BigDecimal.valueOf((correctCount * 1.0) / timesUsed)
                        .setScale(4, RoundingMode.HALF_UP);

                // Discrimination Index calculation
                di = calculateDiscriminationIndex(q.getQuestionId(), answers, attemptMap);
            }

            QuestionStat stat = questionStatRepository.findById(q.getQuestionId())
                    .orElseGet(() -> QuestionStat.builder().questionId(q.getQuestionId()).build());

            stat.setTimesUsed(timesUsed);
            stat.setCorrectRate(correctRate);
            stat.setDiscriminationIndex(di);
            stat.setAvgTimeSeconds(null);
            stat.setUpdatedAt(Instant.now());

            stats.add(stat);
        }

        questionStatRepository.saveAll(stats);
        log.info("Saved {} question stats for period {}", stats.size(), period);
    }

    private BigDecimal calculateDiscriminationIndex(UUID questionId, List<ExamAnswer> answers, Map<UUID, ExamAttempt> attemptMap) {
        // Group attempts by examId
        Map<UUID, List<ExamAttempt>> examAttemptsMap = new HashMap<>();
        for (ExamAnswer ans : answers) {
            ExamAttempt att = attemptMap.get(ans.getAttemptId());
            if (att != null && att.getExamId() != null && att.getTotalScore() != null) {
                examAttemptsMap.computeIfAbsent(att.getExamId(), k -> new ArrayList<>()).add(att);
            }
        }

        List<BigDecimal> examDis = new ArrayList<>();

        for (Map.Entry<UUID, List<ExamAttempt>> entry : examAttemptsMap.entrySet()) {
            List<ExamAttempt> atts = entry.getValue();
            if (atts.size() < 4) {
                continue; // Not enough sample size to form top 27% and bottom 27%
            }

            atts.sort(Comparator.comparing(ExamAttempt::getTotalScore).reversed());

            int k = Math.max(1, (int) Math.round(atts.size() * 0.27));
            List<ExamAttempt> topGroup = atts.subList(0, k);
            List<ExamAttempt> bottomGroup = atts.subList(atts.size() - k, atts.size());

            Set<UUID> topAttemptIds = topGroup.stream().map(ExamAttempt::getAttemptId).collect(Collectors.toSet());
            Set<UUID> bottomAttemptIds = bottomGroup.stream().map(ExamAttempt::getAttemptId).collect(Collectors.toSet());

            long topCorrect = answers.stream()
                    .filter(a -> topAttemptIds.contains(a.getAttemptId()) && Boolean.TRUE.equals(a.getIsCorrect()))
                    .count();
            long bottomCorrect = answers.stream()
                    .filter(a -> bottomAttemptIds.contains(a.getAttemptId()) && Boolean.TRUE.equals(a.getIsCorrect()))
                    .count();

            double pTop = (topCorrect * 1.0) / k;
            double pBottom = (bottomCorrect * 1.0) / k;
            double diff = pTop - pBottom;
            // Clamp to [-1.0, 1.0]
            diff = Math.max(-1.0, Math.min(1.0, diff));
            examDis.add(BigDecimal.valueOf(diff).setScale(4, RoundingMode.HALF_UP));
        }

        if (examDis.isEmpty()) {
            return null;
        }

        // Average DI across exams
        double avgDi = examDis.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        return BigDecimal.valueOf(avgDi).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public void aggregateAiGaps(String period) {
        log.info("Aggregating AI topic gaps for period: {}", period);

        List<AiRefusal> refusals = aiRefusalRepository.findAll().stream()
                .filter(r -> matchesPeriod(r.getCreatedAt(), period))
                .toList();

        // Delete previous stats for this period
        aiTopicGapStatRepository.deleteByPeriod(period);

        if (refusals.isEmpty()) {
            return;
        }

        Map<UUID, AiMessage> messageMap = aiMessageRepository.findAll().stream()
                .collect(Collectors.toMap(AiMessage::getMessageId, m -> m, (a, b) -> a));

        Map<UUID, AiConversation> convMap = aiConversationRepository.findAll().stream()
                .collect(Collectors.toMap(AiConversation::getConversationId, c -> c, (a, b) -> a));

        Map<UUID, Topic> topicMap = topicRepository.findAll().stream()
                .collect(Collectors.toMap(Topic::getTopicId, t -> t, (a, b) -> a));

        // Group refusals by topicId
        Map<UUID, List<AiRefusal>> topicRefusals = new HashMap<>();

        for (AiRefusal ref : refusals) {
            AiMessage msg = messageMap.get(ref.getMessageId());
            if (msg == null) continue;
            AiConversation conv = convMap.get(msg.getConversationId());
            if (conv == null || conv.getTopicId() == null) continue;

            topicRefusals.computeIfAbsent(conv.getTopicId(), k -> new ArrayList<>()).add(ref);
        }

        List<AiTopicGapStat> stats = new ArrayList<>();

        for (Map.Entry<UUID, List<AiRefusal>> entry : topicRefusals.entrySet()) {
            UUID topicId = entry.getKey();
            List<AiRefusal> refs = entry.getValue();

            Topic topic = topicMap.get(topicId);
            UUID subjectId = topic != null ? topic.getSubjectId() : null;

            // Sample query or reason
            String sample = refs.stream()
                    .map(r -> r.getMissingTopicHint() != null ? r.getMissingTopicHint() : r.getReason())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("Nội dung chưa có trong học liệu");

            AiTopicGapStat stat = AiTopicGapStat.builder()
                    .subjectId(subjectId)
                    .topicId(topicId)
                    .refusalCount(refs.size())
                    .frequentQuerySample(sample)
                    .period(period)
                    .generatedAt(Instant.now())
                    .build();

            stats.add(stat);
        }

        aiTopicGapStatRepository.saveAll(stats);
        log.info("Saved {} AI topic gap stats for period {}", stats.size(), period);
    }

    @Override
    @Transactional
    public void aggregateMaterialEffectiveness(String period) {
        log.info("Aggregating material effectiveness for period: {}", period);

        List<LearningMaterial> materials = learningMaterialRepository.findAll();
        if (materials.isEmpty()) {
            return;
        }

        List<ActivityLog> logs = activityLogRepository.findAll().stream()
                .filter(l -> "VIEW_MATERIAL".equalsIgnoreCase(l.getActionType()))
                .filter(l -> matchesPeriod(l.getCreatedAt(), period))
                .toList();

        // Delete previous stats for this period
        materialEffectivenessStatRepository.deleteByPeriod(period);

        // Count views per material
        Map<UUID, Integer> viewCounts = new HashMap<>();
        for (ActivityLog l : logs) {
            if (l.getObjectId() != null) {
                viewCounts.put(l.getObjectId(), viewCounts.getOrDefault(l.getObjectId(), 0) + 1);
            }
        }

        List<MaterialEffectivenessStat> stats = new ArrayList<>();

        for (LearningMaterial lm : materials) {
            int views = viewCounts.getOrDefault(lm.getMaterialId(), 0);

            MaterialEffectivenessStat stat = MaterialEffectivenessStat.builder()
                    .materialId(lm.getMaterialId())
                    .period(period)
                    .viewCount(views)
                    .avgTimeSpentSeconds(null)
                    .correlatedScoreImprovement(null)
                    .build();

            stats.add(stat);
        }

        materialEffectivenessStatRepository.saveAll(stats);
        log.info("Saved {} material effectiveness stats for period {}", stats.size(), period);
    }

    @Override
    @Transactional
    public void triggerFullAggregation(String period) {
        String p = (period != null && !period.isBlank()) ? period : YearMonth.now().toString();
        log.info("Triggering full aggregation for period: {}", p);
        aggregateTopicDifficulty(p);
        aggregateQuestionStats(p);
        aggregateAiGaps(p);
        aggregateMaterialEffectiveness(p);
    }

    private boolean matchesPeriod(Instant instant, String period) {
        if (instant == null || period == null || period.isBlank() || "ALL_TIME".equalsIgnoreCase(period)) {
            return true;
        }
        String ym = YearMonth.from(instant.atZone(ZoneOffset.UTC)).toString();
        return ym.equals(period);
    }
}
