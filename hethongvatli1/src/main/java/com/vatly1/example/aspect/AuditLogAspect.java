package com.vatly1.example.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.dto.request.AdminUpdateUserDTO;
import com.vatly1.example.dto.request.UpdateUserStatusDTO;
import com.vatly1.example.entity.AuditLog;
import com.vatly1.example.entity.ExperimentSubmission;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.EvidenceSourceType;
import com.vatly1.example.repository.AuditLogRepository;
import com.vatly1.example.repository.ExperimentSubmissionRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.IEvidenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final IUserRepository userRepository;
    private final ExperimentSubmissionRepository experimentSubmissionRepository;
    private final IEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    @Around("execution(* com.vatly1.example.service.impl.UserServiceImpl.adminUpdateUser(..)) && args(id, updateDTO)")
    public Object auditAdminUpdateUser(ProceedingJoinPoint pjp, UUID id, AdminUpdateUserDTO updateDTO) throws Throwable {
        User before = userRepository.findById(id).orElse(null);
        String oldRole = before != null && before.getRole() != null ? before.getRole().name() : null;

        Object result = pjp.proceed();

        try {
            String newRole = updateDTO.getRole() != null ? updateDTO.getRole().name() : oldRole;
            AuditLog auditLog = AuditLog.builder()
                    .action("CHANGE_ROLE")
                    .entity("USER")
                    .entityId(id)
                    .oldValue(objectMapper.valueToTree(Map.of("role", oldRole != null ? oldRole : "")))
                    .newValue(objectMapper.valueToTree(Map.of("role", newRole != null ? newRole : "")))
                    .createdAt(Instant.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Error logging audit for adminUpdateUser: {}", e.getMessage(), e);
        }

        return result;
    }

    @Around("execution(* com.vatly1.example.service.impl.UserServiceImpl.adminUpdateUserStatus(..)) && args(id, updateDTO)")
    public Object auditAdminUpdateUserStatus(ProceedingJoinPoint pjp, UUID id, UpdateUserStatusDTO updateDTO) throws Throwable {
        User before = userRepository.findById(id).orElse(null);
        String oldStatus = before != null && before.getStatus() != null ? before.getStatus().name() : null;

        Object result = pjp.proceed();

        try {
            String newStatus = updateDTO.getStatus() != null ? updateDTO.getStatus().name() : oldStatus;
            AuditLog auditLog = AuditLog.builder()
                    .action("CHANGE_STATUS")
                    .entity("USER")
                    .entityId(id)
                    .oldValue(objectMapper.valueToTree(Map.of("status", oldStatus != null ? oldStatus : "")))
                    .newValue(objectMapper.valueToTree(Map.of("status", newStatus != null ? newStatus : "")))
                    .createdAt(Instant.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Error logging audit for adminUpdateUserStatus: {}", e.getMessage(), e);
        }

        return result;
    }

    @Around("execution(* com.vatly1.example.service.impl.LearningMaterialServiceImpl.approveMaterial(..)) && args(materialId)")
    public Object auditApproveMaterial(ProceedingJoinPoint pjp, UUID materialId) throws Throwable {
        Object result = pjp.proceed();

        try {
            AuditLog auditLog = AuditLog.builder()
                    .action("APPROVE_MATERIAL")
                    .entity("LEARNING_MATERIAL")
                    .entityId(materialId)
                    .oldValue(objectMapper.valueToTree(Map.of("status", "PENDING")))
                    .newValue(objectMapper.valueToTree(Map.of("status", "APPROVED")))
                    .createdAt(Instant.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Error logging audit for approveMaterial: {}", e.getMessage(), e);
        }

        return result;
    }

    @Around("execution(* com.vatly1.example.service.impl.QuestionBankServiceImpl.approveQuestion(..)) && args(questionId)")
    public Object auditApproveQuestion(ProceedingJoinPoint pjp, UUID questionId) throws Throwable {
        Object result = pjp.proceed();

        try {
            AuditLog auditLog = AuditLog.builder()
                    .action("APPROVE_QUESTION")
                    .entity("QUESTION")
                    .entityId(questionId)
                    .oldValue(objectMapper.valueToTree(Map.of("status", "DRAFT")))
                    .newValue(objectMapper.valueToTree(Map.of("status", "APPROVED")))
                    .createdAt(Instant.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Error logging audit for approveQuestion: {}", e.getMessage(), e);
        }

        return result;
    }

    @Around("execution(* com.vatly1.example.service.impl.ExperimentServiceImpl.confirmSubmission(..)) && args(submissionId, note, instructorId)")
    public Object auditConfirmSubmission(ProceedingJoinPoint pjp, UUID submissionId, String note, UUID instructorId) throws Throwable {
        Object result = pjp.proceed();

        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(instructorId)
                    .action("CONFIRM_LAB")
                    .entity("EXPERIMENT_SUBMISSION")
                    .entityId(submissionId)
                    .oldValue(objectMapper.valueToTree(Map.of("status", "SUBMITTED")))
                    .newValue(objectMapper.valueToTree(Map.of("status", "CONFIRMED", "note", note != null ? note : "")))
                    .createdAt(Instant.now())
                    .build();
            auditLogRepository.save(auditLog);

            // Tự động tạo hồ sơ minh chứng bài lab đã xác nhận (Evidence Repository)
            ExperimentSubmission sub = experimentSubmissionRepository.findById(submissionId).orElse(null);
            if (sub != null) {
                evidenceService.recordEvidence(sub.getStudentId(), EvidenceSourceType.EXPERIMENT, submissionId, sub.getFileId(), sub.getEvidenceUrl());
            }
        } catch (Exception e) {
            log.error("Error logging audit for confirmSubmission: {}", e.getMessage(), e);
        }

        return result;
    }
}