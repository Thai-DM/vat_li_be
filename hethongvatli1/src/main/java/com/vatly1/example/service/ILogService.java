package com.vatly1.example.service;

import com.vatly1.example.entity.ActivityLog;
import com.vatly1.example.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ILogService {
    Page<ActivityLog> getAdminActivityLogs(UUID userId, String actionType, Instant startDate, Instant endDate, Pageable pageable);
    Page<AuditLog> getAdminAuditLogs(String entity, UUID userId, Pageable pageable);
    List<ActivityLog> getClassActivityLogs(UUID classId, UUID requesterId, String role);
    List<ActivityLog> getStudentActivityLogs(UUID studentId);
}
