package com.vatly1.example.service;

import com.vatly1.example.dto.BulkEnrollmentDTO;
import com.vatly1.example.dto.EnrollmentDTO;
import com.vatly1.example.dto.SingleEnrollmentDTO;
import com.vatly1.example.dto.UpdateEnrollmentStatusDTO;
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
