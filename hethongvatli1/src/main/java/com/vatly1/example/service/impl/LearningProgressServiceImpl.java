package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.LearningProgress;
import com.vatly1.example.entity.MaterialProgress;
import com.vatly1.example.entity.enums.ProgressStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.LearningProgressDTO;
import com.vatly1.example.model.dto.MaterialProgressDTO;
import com.vatly1.example.model.request.UpdateLearningProgressDTO;
import com.vatly1.example.model.request.UpdateMaterialProgressDTO;
import com.vatly1.example.repository.*;
import com.vatly1.example.service.ILearningProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningProgressServiceImpl implements ILearningProgressService {

    private final LearningProgressRepository progressRepository;
    private final MaterialProgressRepository materialProgressRepository;
    private final LearningMaterialRepository learningMaterialRepository;
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
        if (classId == null) {
            return progressRepository.findByStudentId(studentId).stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        }
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

        // Nếu có materialId -> Cập nhật chi tiết học liệu trước
        if (dto.getMaterialId() != null) {
            updateMaterialProgress(studentId, UpdateMaterialProgressDTO.builder()
                    .classId(dto.getClassId())
                    .materialId(dto.getMaterialId())
                    .progressPercent(dto.getProgressPercent())
                    .timeSpentSeconds(dto.getTimeSpentSeconds())
                    .build());

            UUID topicId = dto.getTopicId();
            if (topicId == null) {
                LearningMaterial m = learningMaterialRepository.findById(dto.getMaterialId()).orElse(null);
                if (m != null) topicId = m.getTopicId();
            }

            if (topicId != null) {
                Optional<LearningProgress> lpOpt = progressRepository.findByStudentIdAndClassIdAndTopicId(
                        studentId, dto.getClassId(), topicId
                );
                if (lpOpt.isPresent()) {
                    return mapToDTO(lpOpt.get());
                }
            }
        }

        if (dto.getTopicId() == null) {
            throw new CustomException("Topic ID is required", HttpStatus.BAD_REQUEST);
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

    @Override
    @Transactional
    public MaterialProgressDTO updateMaterialProgress(UUID studentId, UpdateMaterialProgressDTO dto) {
        Class clazz = classRepository.findById(dto.getClassId())
                .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));

        if (!enrollmentRepository.existsByClassIdAndStudentId(dto.getClassId(), studentId)) {
            throw new CustomException("Sinh viên chưa ghi danh vào lớp học này", HttpStatus.FORBIDDEN);
        }

        LearningMaterial material = learningMaterialRepository.findById(dto.getMaterialId())
                .orElseThrow(() -> new CustomException("Learning material not found", HttpStatus.NOT_FOUND));

        ProgressStatus computedStatus;
        if (dto.getProgressPercent().compareTo(BigDecimal.valueOf(100)) >= 0) {
            computedStatus = ProgressStatus.COMPLETED;
        } else if (dto.getProgressPercent().compareTo(BigDecimal.ZERO) <= 0) {
            computedStatus = ProgressStatus.NOT_STARTED;
        } else {
            computedStatus = ProgressStatus.IN_PROGRESS;
        }

        Optional<MaterialProgress> existingOpt = materialProgressRepository.findByStudentIdAndClassIdAndMaterialId(
                studentId, dto.getClassId(), dto.getMaterialId()
        );

        MaterialProgress mp;
        if (existingOpt.isPresent()) {
            mp = existingOpt.get();
            mp.setProgressPercent(dto.getProgressPercent());
            mp.setStatus(computedStatus);
            if (computedStatus == ProgressStatus.COMPLETED && mp.getCompletedAt() == null) {
                mp.setCompletedAt(Instant.now());
            }
            if (dto.getTimeSpentSeconds() != null) {
                int prevTime = mp.getTimeSpentSeconds() != null ? mp.getTimeSpentSeconds() : 0;
                mp.setTimeSpentSeconds(prevTime + dto.getTimeSpentSeconds());
            }
            mp.setLastAccessedAt(Instant.now());
        } else {
            mp = MaterialProgress.builder()
                    .studentId(studentId)
                    .classId(dto.getClassId())
                    .materialId(dto.getMaterialId())
                    .topicId(material.getTopicId() != null ? material.getTopicId() : UUID.randomUUID())
                    .status(computedStatus)
                    .progressPercent(dto.getProgressPercent())
                    .timeSpentSeconds(dto.getTimeSpentSeconds() != null ? dto.getTimeSpentSeconds() : 0)
                    .completedAt(computedStatus == ProgressStatus.COMPLETED ? Instant.now() : null)
                    .lastAccessedAt(Instant.now())
                    .build();
        }

        mp = materialProgressRepository.save(mp);

        // Tự động tính toán lại và đồng bộ tiến độ của Topic
        if (material.getTopicId() != null) {
            recalculateTopicProgress(studentId, dto.getClassId(), material.getTopicId());
        }

        return mapToMaterialDTO(mp);
    }

    @Override
    public List<MaterialProgressDTO> getMyMaterialProgress(UUID studentId, UUID classId, UUID topicId) {
        if (topicId != null && classId != null) {
            return materialProgressRepository.findByStudentIdAndClassIdAndTopicId(studentId, classId, topicId).stream()
                    .map(this::mapToMaterialDTO)
                    .collect(Collectors.toList());
        }
        if (classId != null) {
            return materialProgressRepository.findByStudentIdAndClassId(studentId, classId).stream()
                    .map(this::mapToMaterialDTO)
                    .collect(Collectors.toList());
        }
        return materialProgressRepository.findByStudentId(studentId).stream()
                .map(this::mapToMaterialDTO)
                .collect(Collectors.toList());
    }

    private void recalculateTopicProgress(UUID studentId, UUID classId, UUID topicId) {
        List<LearningMaterial> topicMaterials = learningMaterialRepository.findByTopicIdOrderByCreatedAtDesc(topicId);
        if (topicMaterials.isEmpty()) {
            return;
        }

        List<UUID> completedIds = materialProgressRepository.findCompletedMaterialIdsByStudentIdAndClassIdAndTopicId(
                studentId, classId, topicId
        );

        long completedCount = topicMaterials.stream()
                .filter(m -> completedIds.contains(m.getMaterialId()))
                .count();

        BigDecimal calculatedPercent = BigDecimal.valueOf(completedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(topicMaterials.size()), 2, RoundingMode.HALF_UP);

        ProgressStatus topicStatus;
        if (calculatedPercent.compareTo(BigDecimal.valueOf(100)) >= 0) {
            topicStatus = ProgressStatus.COMPLETED;
        } else if (calculatedPercent.compareTo(BigDecimal.ZERO) <= 0) {
            topicStatus = ProgressStatus.NOT_STARTED;
        } else {
            topicStatus = ProgressStatus.IN_PROGRESS;
        }

        Optional<LearningProgress> lpOpt = progressRepository.findByStudentIdAndClassIdAndTopicId(studentId, classId, topicId);
        LearningProgress lp = lpOpt.orElseGet(() -> LearningProgress.builder()
                .studentId(studentId)
                .classId(classId)
                .topicId(topicId)
                .build());

        lp.setProgressPercent(calculatedPercent);
        lp.setStatus(topicStatus);
        lp.setLastAccessedAt(Instant.now());
        progressRepository.save(lp);
    }

    private LearningProgressDTO mapToDTO(LearningProgress progress) {
        List<UUID> completedIds = materialProgressRepository.findCompletedMaterialIdsByStudentIdAndClassIdAndTopicId(
                progress.getStudentId(), progress.getClassId(), progress.getTopicId()
        );
        int total = learningMaterialRepository.findByTopicIdOrderByCreatedAtDesc(progress.getTopicId()).size();

        return LearningProgressDTO.builder()
                .progressId(progress.getProgressId())
                .studentId(progress.getStudentId())
                .classId(progress.getClassId())
                .topicId(progress.getTopicId())
                .status(progress.getStatus())
                .progressPercent(progress.getProgressPercent())
                .lastAccessedAt(progress.getLastAccessedAt())
                .completedMaterialIds(completedIds)
                .totalMaterials(total)
                .completedMaterials(completedIds.size())
                .build();
    }

    private MaterialProgressDTO mapToMaterialDTO(MaterialProgress mp) {
        return MaterialProgressDTO.builder()
                .progressId(mp.getProgressId())
                .studentId(mp.getStudentId())
                .classId(mp.getClassId())
                .materialId(mp.getMaterialId())
                .topicId(mp.getTopicId())
                .status(mp.getStatus())
                .progressPercent(mp.getProgressPercent())
                .timeSpentSeconds(mp.getTimeSpentSeconds())
                .completedAt(mp.getCompletedAt())
                .lastAccessedAt(mp.getLastAccessedAt())
                .build();
    }
}