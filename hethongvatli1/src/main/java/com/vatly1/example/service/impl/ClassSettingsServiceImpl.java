package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassSettings;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ClassSettingsDTO;
import com.vatly1.example.model.request.UpdateClassSettingsDTO;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassSettingsRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.service.IClassSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClassSettingsServiceImpl implements IClassSettingsService {

    private final IClassSettingsRepository classSettingsRepository;
    private final IClassRepository classRepository;
    private final IClassStaffRepository classStaffRepository;
    private final IClassEnrollmentRepository classEnrollmentRepository;

    @Override
    @Transactional
    public ClassSettingsDTO getClassSettings(UUID classId, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role == UserRole.ADMIN) {
            // ADMIN has full read access
        } else if (role == UserRole.INSTRUCTOR || role == UserRole.TA) {
            boolean isOwner = Objects.equals(clazz.getInstructorId(), currentUserId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(classId, currentUserId);
            if (!isOwner && !isStaff) {
                throw new CustomException("Bạn không có quyền xem cấu hình lớp học này", HttpStatus.FORBIDDEN);
            }
        } else if (role == UserRole.STUDENT) {
            boolean isEnrolled = classEnrollmentRepository.isStudentActivelyEnrolledInClass(classId, currentUserId);
            if (!isEnrolled) {
                throw new CustomException("Bạn không có quyền xem cấu hình lớp học này", HttpStatus.FORBIDDEN);
            }
        } else {
            throw new CustomException("Không có quyền truy cập", HttpStatus.FORBIDDEN);
        }

        ClassSettings settings = classSettingsRepository.findById(classId)
                .orElseGet(() -> createDefaultSettings(classId));

        return toDTO(settings);
    }

    @Override
    @Transactional
    public ClassSettingsDTO updateClassSettings(UUID classId, UpdateClassSettingsDTO dto, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role == UserRole.ADMIN) {
            // Admin allowed
        } else if (role == UserRole.INSTRUCTOR) {
            boolean isOwner = Objects.equals(clazz.getInstructorId(), currentUserId);
            boolean isInstructorStaff = classStaffRepository.existsByClassIdAndUserIdAndRoleInClass(classId, currentUserId, com.vatly1.example.entity.enums.ClassStaffRole.INSTRUCTOR);
            if (!isOwner && !isInstructorStaff) {
                throw new CustomException("Bạn không có quyền thay đổi cấu hình lớp học này", HttpStatus.FORBIDDEN);
            }
        } else {
            throw new CustomException("Chỉ Giảng viên phụ trách hoặc Admin mới có quyền cập nhật cấu hình lớp học", HttpStatus.FORBIDDEN);
        }

        ClassSettings settings = classSettingsRepository.findById(classId)
                .orElseGet(() -> createDefaultSettings(classId));

        if (dto.getPracticeEnabled() != null) {
            settings.setPracticeEnabled(dto.getPracticeEnabled());
        }
        if (dto.getQuizEnabled() != null) {
            settings.setQuizEnabled(dto.getQuizEnabled());
        }
        if (dto.getMaxPracticeAttempts() != null) {
            if (dto.getMaxPracticeAttempts() <= 0) {
                throw new CustomException("Số lượt luyện tập tối đa phải lớn hơn 0", HttpStatus.BAD_REQUEST);
            }
            settings.setMaxPracticeAttempts(dto.getMaxPracticeAttempts());
        }
        if (dto.getPracticeShowSolution() != null) {
            settings.setPracticeShowSolution(dto.getPracticeShowSolution());
        }

        settings.setUpdatedBy(currentUserId);
        settings.setUpdatedAt(Instant.now());

        ClassSettings saved = classSettingsRepository.save(settings);
        log.info("ClassSettings updated for classId: {} by user: {}", classId, currentUserId);
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassSettings getEffectiveSettings(UUID classId) {
        if (classId == null) {
            return getDefaultSettingsInstance(null);
        }
        return classSettingsRepository.findById(classId)
                .orElseGet(() -> getDefaultSettingsInstance(classId));
    }

    private ClassSettings createDefaultSettings(UUID classId) {
        ClassSettings settings = ClassSettings.builder()
                .classId(classId)
                .practiceEnabled(true)
                .quizEnabled(true)
                .maxPracticeAttempts(10)
                .practiceShowSolution(true)
                .updatedAt(Instant.now())
                .build();
        return classSettingsRepository.save(settings);
    }

    private ClassSettings getDefaultSettingsInstance(UUID classId) {
        return ClassSettings.builder()
                .classId(classId)
                .practiceEnabled(true)
                .quizEnabled(true)
                .maxPracticeAttempts(10)
                .practiceShowSolution(true)
                .build();
    }

    private ClassSettingsDTO toDTO(ClassSettings settings) {
        return ClassSettingsDTO.builder()
                .classId(settings.getClassId())
                .practiceEnabled(settings.getPracticeEnabled())
                .quizEnabled(settings.getQuizEnabled())
                .maxPracticeAttempts(settings.getMaxPracticeAttempts())
                .practiceShowSolution(settings.getPracticeShowSolution())
                .updatedAt(settings.getUpdatedAt())
                .updatedBy(settings.getUpdatedBy())
                .build();
    }
}
