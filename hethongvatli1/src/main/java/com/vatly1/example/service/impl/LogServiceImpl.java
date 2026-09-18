package com.vatly1.example.service.impl;

import com.vatly1.example.entity.ActivityLog;
import com.vatly1.example.entity.AuditLog;
import com.vatly1.example.entity.Class;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.ActivityLogRepository;
import com.vatly1.example.repository.AuditLogRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.service.ILogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogServiceImpl implements ILogService {

    private final ActivityLogRepository activityLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final IClassRepository classRepository;
    private final IClassStaffRepository classStaffRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ActivityLog> getAdminActivityLogs(UUID userId, String actionType, Instant startDate, Instant endDate, Pageable pageable) {
        Specification<ActivityLog> spec = (root, query, cb) -> cb.conjunction();

        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (actionType != null && !actionType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actionType"), actionType));
        }
        if (startDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
        }
        if (endDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
        }

        return activityLogRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLog> getAdminAuditLogs(String entity, UUID userId, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();

        if (entity != null && !entity.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entity"), entity));
        }
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }

        return auditLogRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityLog> getClassActivityLogs(UUID classId, UUID requesterId, String role) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Lớp học không tồn tại", HttpStatus.NOT_FOUND));

        if (role != null && role.contains("ADMIN")) {
            return activityLogRepository.findByClassIdOrderByCreatedAtDesc(classId);
        }

        if (role != null && role.contains("INSTRUCTOR")) {
            boolean isInstructor = clazz.getInstructorId() != null && clazz.getInstructorId().equals(requesterId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(classId, requesterId);
            if (!isInstructor && !isStaff) {
                throw new CustomException("Bạn không phụ trách lớp này", HttpStatus.FORBIDDEN);
            }
            return activityLogRepository.findByClassIdOrderByCreatedAtDesc(classId);
        }

        throw new CustomException("Bạn không có quyền xem nhật ký hoạt động của lớp này", HttpStatus.FORBIDDEN);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityLog> getStudentActivityLogs(UUID studentId) {
        return activityLogRepository.findByUserIdOrderByCreatedAtDesc(studentId);
    }
}