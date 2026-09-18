package com.vatly1.example.service;

import com.vatly1.example.model.request.BulkEnrollmentDTO;
import com.vatly1.example.model.dto.EnrollmentDTO;
import com.vatly1.example.model.request.SingleEnrollmentDTO;
import com.vatly1.example.model.request.UpdateEnrollmentStatusDTO;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface IClassEnrollmentService {
    Page<EnrollmentDTO> getClassStudents(UUID classId, EnrollmentStatus status, int page, int size, UUID currentUserId, String currentUserRole);
    void enrollSingleStudent(UUID classId, SingleEnrollmentDTO dto, UUID currentUserId, String currentUserRole);
    void enrollBulkStudents(UUID classId, BulkEnrollmentDTO dto, UUID currentUserId, String currentUserRole);
    void removeStudent(UUID classId, UUID studentId, UUID currentUserId, String currentUserRole);
    void updateEnrollmentStatus(UUID classId, UUID studentId, UpdateEnrollmentStatusDTO dto, UUID currentUserId, String currentUserRole);
}