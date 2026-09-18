package com.vatly1.example.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.dto.AiConversationDTO;
import com.vatly1.example.dto.ExamAttemptDTO;
import com.vatly1.example.entity.ActivityLog;
import com.vatly1.example.repository.ActivityLogRepository;
import com.vatly1.example.entity.enums.EvidenceSourceType;
import com.vatly1.example.service.IEvidenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityLogAspect {

    private final ActivityLogRepository activityLogRepository;
    private final IEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    @AfterReturning(pointcut = "execution(* com.vatly1.example.service.impl.ExamServiceImpl.startAttempt(..)) && args(examId, studentId)", returning = "result", argNames = "joinPoint,result,examId,studentId")
    @Async
    public void logStartAttempt(JoinPoint joinPoint, Object result, UUID examId, UUID studentId) {
        try {
            ExamAttemptDTO attempt = (ExamAttemptDTO) result;
            ActivityLog logEntry = ActivityLog.builder()
                    .userId(studentId)
                    .actionType("START_EXAM")
                    .objectType("EXAM")
                    .objectId(examId)
                    .metadataJson(objectMapper.valueToTree(Map.of("attemptId", attempt.getAttemptId().toString())))
                    .createdAt(Instant.now())
                    .build();
            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Error logging startAttempt activity: {}", e.getMessage(), e);
        }
    }

    @AfterReturning(pointcut = "execution(* com.vatly1.example.service.impl.ExamServiceImpl.submitAttempt(..)) && args(attemptId, studentId)", returning = "result", argNames = "joinPoint,result,attemptId,studentId")
    @Async
    public void logSubmitAttempt(JoinPoint joinPoint, Object result, UUID attemptId, UUID studentId) {
        try {
            ExamAttemptDTO attempt = (ExamAttemptDTO) result;
            ActivityLog logEntry = ActivityLog.builder()
                    .userId(studentId)
                    .actionType("SUBMIT_EXAM")
                    .objectType("EXAM_ATTEMPT")
                    .objectId(attemptId)
                    .metadataJson(objectMapper.valueToTree(Map.of("score", attempt.getTotalScore() != null ? attempt.getTotalScore() : 0)))
                    .createdAt(Instant.now())
                    .build();
            activityLogRepository.save(logEntry);

            // Tự động tạo hồ sơ minh chứng bài thi (Evidence Repository)
            evidenceService.recordEvidence(studentId, EvidenceSourceType.EXAM, attemptId, null, null);
        } catch (Exception e) {
            log.error("Error logging submitAttempt activity: {}", e.getMessage(), e);
        }
    }

    @AfterReturning(pointcut = "execution(* com.vatly1.example.service.impl.AiTutorServiceImpl.endConversation(..)) && args(conversationId, studentId)", returning = "result", argNames = "joinPoint,result,conversationId,studentId")
    @Async
    public void logEndAiConversation(JoinPoint joinPoint, Object result, UUID conversationId, UUID studentId) {
        try {
            evidenceService.recordEvidence(studentId, EvidenceSourceType.AI_CONVERSATION, conversationId, null, null);
        } catch (Exception e) {
            log.error("Error recording AI conversation evidence: {}", e.getMessage(), e);
        }
    }

    @AfterReturning(pointcut = "execution(* com.vatly1.example.service.impl.ExperimentServiceImpl.submitExperiment(..)) && args(assignmentId, dto, studentId)", argNames = "joinPoint,assignmentId,dto,studentId")
    @Async
    public void logSubmitExperiment(JoinPoint joinPoint, UUID assignmentId, Object dto, UUID studentId) {
        try {
            ActivityLog logEntry = ActivityLog.builder()
                    .userId(studentId)
                    .actionType("SUBMIT_LAB")
                    .objectType("EXPERIMENT_ASSIGNMENT")
                    .objectId(assignmentId)
                    .createdAt(Instant.now())
                    .build();
            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Error logging submitExperiment activity: {}", e.getMessage(), e);
        }
    }

    @AfterReturning(pointcut = "execution(* com.vatly1.example.service.impl.AiTutorServiceImpl.startConversation(..)) && args(dto, studentId)", returning = "result", argNames = "joinPoint,result,dto,studentId")
    @Async
    public void logStartAiConversation(JoinPoint joinPoint, Object result, Object dto, UUID studentId) {
        try {
            AiConversationDTO conv = (AiConversationDTO) result;
            ActivityLog logEntry = ActivityLog.builder()
                    .userId(studentId)
                    .classId(conv.getClassId())
                    .actionType("OPEN_AI_CHAT")
                    .objectType("AI_CONVERSATION")
                    .objectId(conv.getConversationId())
                    .createdAt(Instant.now())
                    .build();
            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Error logging startAiConversation activity: {}", e.getMessage(), e);
        }
    }

    @AfterReturning(pointcut = "execution(* com.vatly1.example.service.impl.LearningMaterialServiceImpl.getMaterialsByTopic(..)) && args(topicId, role)", argNames = "joinPoint,topicId,role")
    @Async
    public void logViewMaterial(JoinPoint joinPoint, UUID topicId, String role) {
        try {
            ActivityLog logEntry = ActivityLog.builder()
                    .actionType("VIEW_MATERIAL")
                    .objectType("TOPIC")
                    .objectId(topicId)
                    .createdAt(Instant.now())
                    .build();
            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Error logging viewMaterial activity: {}", e.getMessage(), e);
        }
    }
}
