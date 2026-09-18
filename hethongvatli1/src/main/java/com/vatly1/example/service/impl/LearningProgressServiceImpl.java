package com.vatly1.example.service.impl;

import com.vatly1.example.dto.LearningProgressDTO;
import com.vatly1.example.dto.UpdateLearningProgressDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.LearningProgress;
import com.vatly1.example.entity.enums.ProgressStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.LearningProgressRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.ILearningProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningProgressServiceImpl implements ILearningProgressService {

    private final LearningProgressRepository progressRepository;
    private final IClassRepository classRepository;
    private final TopicRepository topicRepository;
    private final IClassEnrollmentRepository enrollmentRepository;
    private final IClassStaffRepository classStaffRepository;

    @Override
    public List<LearningProgressDTO> getProgressByClass(UUID classId, UUID studentId, String role) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));

        if ("INSTRUCTOR".equals(role)) {
            boolean isAssignedInstructor = clazz.getInstructorId() != null && clazz.getInstructorId().equals(studentId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(classId, studentId);
            if (!isAssignedInstructor && !isStaff) {
                throw new CustomException("Access denied: You are not teaching this class", HttpStatus.FORBIDDEN);
            }
        }

        List<LearningProgress> progresses;
        if ("STUDENT".equals(role)) {
            progresses = progressRepository.findByClassIdAndStudentId(classId, studentId);
        } else {
            // ADMIN or INSTRUCTOR can see all students in the class
            progresses = progressRepository.findByClassId(classId);
        }

        return progresses.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<LearningProgressDTO> getMyProgress(UUID studentId, UUID classId) {
        if (!classRepository.existsById(classId)) {
            throw new CustomException("Class not found", HttpStatus.NOT_FOUND);
        }
        if (!enrollmentRepository.existsByClassIdAndStudentId(classId, studentId)) {
            throw new CustomException("Sinh viên chưa ghi danh vào lớp học này", HttpStatus.FORBIDDEN);
        }
        return progressRepository.findByClassIdAndStudentId(classId, studentId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LearningProgressDTO updateProgress(UUID studentId, UpdateLearningProgressDTO dto) {
        if (!classRepository.existsById(dto.getClassId())) {
            throw new CustomException("Class not found", HttpStatus.NOT_FOUND);
        }
        if (!enrollmentRepository.existsByClassIdAndStudentId(dto.getClassId(), studentId)) {
            throw new CustomException("Sinh viên chưa ghi danh vào lớp học này", HttpStatus.FORBIDDEN);
        }
        if (!topicRepository.existsById(dto.getTopicId())) {
            throw new CustomException("Topic not found", HttpStatus.NOT_FOUND);
        }

        Optional<LearningProgress> existingOpt = progressRepository.findByStudentIdAndClassIdAndTopicId(
                studentId, dto.getClassId(), dto.getTopicId()
        );

        ProgressStatus computedStatus;
        if (dto.getProgressPercent().compareTo(BigDecimal.valueOf(100)) >= 0) {
            computedStatus = ProgressStatus.COMPLETED;
        } else if (dto.getProgressPercent().compareTo(BigDecimal.ZERO) <= 0) {
            computedStatus = ProgressStatus.NOT_STARTED;
        } else {
            computedStatus = ProgressStatus.IN_PROGRESS;
        }

        LearningProgress progress;
        if (existingOpt.isPresent()) {
            progress = existingOpt.get();
            progress.setProgressPercent(dto.getProgressPercent());
            progress.setStatus(computedStatus);
            progress.setLastAccessedAt(Instant.now());
        } else {
            progress = LearningProgress.builder()
                    .studentId(studentId)
                    .classId(dto.getClassId())
                    .topicId(dto.getTopicId())
                    .progressPercent(dto.getProgressPercent())
                    .status(computedStatus)
                    .lastAccessedAt(Instant.now())
                    .build();
        }

        progress = progressRepository.save(progress);
        return mapToDTO(progress);
    }

    private LearningProgressDTO mapToDTO(LearningProgress progress) {
        return LearningProgressDTO.builder()
                .progressId(progress.getProgressId())
                .studentId(progress.getStudentId())
                .classId(progress.getClassId())
                .topicId(progress.getTopicId())
                .status(progress.getStatus())
                .progressPercent(progress.getProgressPercent())
                .lastAccessedAt(progress.getLastAccessedAt())
                .build();
    }
}
