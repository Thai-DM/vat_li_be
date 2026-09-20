package com.vatly1.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.model.response.DashboardDataDTO;
import com.vatly1.example.model.response.DashboardSnapshotDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.DashboardSnapshot;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.ExperimentAssignment;
import com.vatly1.example.entity.ExperimentSubmission;
import com.vatly1.example.entity.LearningProgress;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.enums.ProgressStatus;
import com.vatly1.example.entity.enums.SubmissionStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.DashboardSnapshotRepository;
import com.vatly1.example.repository.ExperimentAssignmentRepository;
import com.vatly1.example.repository.ExperimentSubmissionRepository;
import com.vatly1.example.repository.IAiConversationRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.LearningProgressRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements IDashboardService {

    private final DashboardSnapshotRepository snapshotRepository;
    private final IClassRepository classRepository;
    private final IClassStaffRepository classStaffRepository;
    private final IClassEnrollmentRepository classEnrollmentRepository;
    private final IExamRepository examRepository;
    private final IExamAttemptRepository examAttemptRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final TopicRepository topicRepository;
    private final ExperimentAssignmentRepository experimentAssignmentRepository;
    private final ExperimentSubmissionRepository experimentSubmissionRepository;
    private final IAiConversationRepository aiConversationRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public DashboardSnapshotDTO getClassDashboard(UUID classId, UUID requesterId, String role) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Lớp học không tồn tại", HttpStatus.NOT_FOUND));

        validateAccess(clazz, requesterId, role);

        Optional<DashboardSnapshot> snapshotOpt = snapshotRepository
                .findTopByClassIdAndStudentIdIsNullOrderByGeneratedAtDesc(classId);

        if (snapshotOpt.isPresent()) {
            return mapToDTO(snapshotOpt.get());
        }

        // Nếu chưa có snapshot, tính toán ngay
        return computeClassSnapshot(clazz);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSnapshotDTO getStudentDashboard(UUID classId, UUID studentId, UUID requesterId, String role) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Lớp học không tồn tại", HttpStatus.NOT_FOUND));

        if (role != null && role.contains("STUDENT")) {
            throw new CustomException("Học sinh không có quyền truy cập dashboard này", HttpStatus.FORBIDDEN);
        }

        validateAccess(clazz, requesterId, role);

        if (!classEnrollmentRepository.existsByClassIdAndStudentId(classId, studentId)) {
            throw new CustomException("Sinh viên không thuộc lớp học này", HttpStatus.NOT_FOUND);
        }

        Optional<DashboardSnapshot> snapshotOpt = snapshotRepository
                .findTopByClassIdAndStudentIdOrderByGeneratedAtDesc(classId, studentId);

        if (snapshotOpt.isPresent()) {
            return mapToDTO(snapshotOpt.get());
        }

        return computeStudentSnapshot(clazz, studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSnapshotDTO getMyDashboard(UUID studentId) {
        Optional<DashboardSnapshot> snapshotOpt = snapshotRepository
                .findTopByStudentIdOrderByGeneratedAtDesc(studentId);

        if (snapshotOpt.isPresent()) {
            return mapToDTO(snapshotOpt.get());
        }

        return computePersonalSnapshot(studentId);
    }

    @Override
    @Transactional
    public DashboardSnapshotDTO regenerateClassSnapshot(UUID classId) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Lớp học không tồn tại", HttpStatus.NOT_FOUND));

        DashboardDataDTO data = calculateClassMetrics(clazz);

        DashboardSnapshot snapshot = DashboardSnapshot.builder()
                .classId(classId)
                .studentId(null)
                .period("ALL_TIME")
                .dataJson(objectMapper.valueToTree(data))
                .generatedAt(Instant.now())
                .build();

        snapshot = snapshotRepository.save(snapshot);
        return mapToDTO(snapshot);
    }

    private void validateAccess(Class clazz, UUID requesterId, String role) {
        if (role != null && role.contains("ADMIN")) {
            return;
        }

        if (role != null && role.contains("INSTRUCTOR")) {
            boolean isInstructor = clazz.getInstructorId() != null && clazz.getInstructorId().equals(requesterId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), requesterId);
            if (!isInstructor && !isStaff) {
                throw new CustomException("Bạn không phụ trách lớp này", HttpStatus.FORBIDDEN);
            }
            return;
        }

        throw new CustomException("Bạn không có quyền truy cập lớp học này", HttpStatus.FORBIDDEN);
    }

    private DashboardSnapshotDTO computeClassSnapshot(Class clazz) {
        DashboardDataDTO data = calculateClassMetrics(clazz);
        DashboardSnapshot snapshot = DashboardSnapshot.builder()
                .classId(clazz.getClassId())
                .studentId(null)
                .period("ALL_TIME")
                .dataJson(objectMapper.valueToTree(data))
                .generatedAt(Instant.now())
                .build();
        return mapToDTO(snapshot);
    }

    private DashboardDataDTO calculateClassMetrics(Class clazz) {
        UUID classId = clazz.getClassId();

        // 1. Exam attempts
        List<Exam> exams = examRepository.findByClassId(classId);
        List<ExamAttempt> attempts = new ArrayList<>();
        for (Exam exam : exams) {
            attempts.addAll(examAttemptRepository.findByExamId(exam.getExamId()));
        }
        List<ExamAttempt> gradedAttempts = attempts.stream()
                .filter(a -> a.getTotalScore() != null)
                .toList();

        double avgScore = gradedAttempts.isEmpty() ? 0.0 :
                gradedAttempts.stream().mapToDouble(a -> a.getTotalScore().doubleValue()).average().orElse(0.0);
        avgScore = Math.round(avgScore * 100.0) / 100.0;

        // 2. Topics
        int totalTopics = 0;
        if (clazz.getSubjectId() != null) {
            List<Topic> topics = topicRepository.findBySubjectIdOrderByOrderIndexAsc(clazz.getSubjectId());
            totalTopics = topics.size();
        }

        List<LearningProgress> progresses = learningProgressRepository.findByClassId(classId);
        int completedTopics = (int) progresses.stream()
                .filter(p -> p.getStatus() == ProgressStatus.COMPLETED)
                .count();

        // 3. Labs confirmed
        List<ExperimentAssignment> assignments = experimentAssignmentRepository.findByClassId(classId);
        int labsConfirmed = 0;
        for (ExperimentAssignment assignment : assignments) {
            List<ExperimentSubmission> subs = experimentSubmissionRepository.findByAssignmentId(assignment.getAssignmentId());
            labsConfirmed += (int) subs.stream()
                    .filter(s -> s.getStatus() == SubmissionStatus.CONFIRMED)
                    .count();
        }

        // 4. AI Sessions
        int aiSessionsCount = aiConversationRepository.findByClassId(classId).size();

        return DashboardDataDTO.builder()
                .avgScore(avgScore)
                .completedTopics(completedTopics)
                .totalTopics(totalTopics)
                .labsConfirmed(labsConfirmed)
                .aiSessionsCount(aiSessionsCount)
                .totalExamsTaken(gradedAttempts.size())
                .lastUpdated(Instant.now())
                .build();
    }

    private DashboardSnapshotDTO computeStudentSnapshot(Class clazz, UUID studentId) {
        UUID classId = clazz.getClassId();

        // 1. Exam attempts của student trong lớp này
        List<Exam> exams = examRepository.findByClassId(classId);
        List<UUID> examIds = exams.stream().map(Exam::getExamId).toList();
        List<ExamAttempt> allStudentAttempts = examAttemptRepository.findByStudentId(studentId);
        List<ExamAttempt> classAttempts = allStudentAttempts.stream()
                .filter(a -> examIds.contains(a.getExamId()) && a.getTotalScore() != null)
                .toList();

        double avgScore = classAttempts.isEmpty() ? 0.0 :
                classAttempts.stream().mapToDouble(a -> a.getTotalScore().doubleValue()).average().orElse(0.0);
        avgScore = Math.round(avgScore * 100.0) / 100.0;

        // 2. Topics
        int totalTopics = 0;
        if (clazz.getSubjectId() != null) {
            List<Topic> topics = topicRepository.findBySubjectIdOrderByOrderIndexAsc(clazz.getSubjectId());
            totalTopics = topics.size();
        }
        List<LearningProgress> progresses = learningProgressRepository.findByClassIdAndStudentId(classId, studentId);
        int completedTopics = (int) progresses.stream()
                .filter(p -> p.getStatus() == ProgressStatus.COMPLETED)
                .count();

        // 3. Labs confirmed
        List<ExperimentAssignment> assignments = experimentAssignmentRepository.findByClassId(classId);
        List<UUID> assignmentIds = assignments.stream().map(ExperimentAssignment::getAssignmentId).toList();
        List<ExperimentSubmission> studentSubs = experimentSubmissionRepository.findByStudentId(studentId);
        int labsConfirmed = (int) studentSubs.stream()
                .filter(s -> assignmentIds.contains(s.getAssignmentId()) && s.getStatus() == SubmissionStatus.CONFIRMED)
                .count();

        // 4. AI Sessions
        int aiSessionsCount = (int) aiConversationRepository.findByClassId(classId).stream()
                .filter(c -> studentId.equals(c.getStudentId()))
                .count();

        DashboardDataDTO data = DashboardDataDTO.builder()
                .avgScore(avgScore)
                .completedTopics(completedTopics)
                .totalTopics(totalTopics)
                .labsConfirmed(labsConfirmed)
                .aiSessionsCount(aiSessionsCount)
                .totalExamsTaken(classAttempts.size())
                .lastUpdated(Instant.now())
                .build();

        DashboardSnapshot snapshot = DashboardSnapshot.builder()
                .classId(classId)
                .studentId(studentId)
                .period("ALL_TIME")
                .dataJson(objectMapper.valueToTree(data))
                .generatedAt(Instant.now())
                .build();

        return mapToDTO(snapshot);
    }

    private DashboardSnapshotDTO computePersonalSnapshot(UUID studentId) {
        List<ExamAttempt> attempts = examAttemptRepository.findByStudentId(studentId).stream()
                .filter(a -> a.getTotalScore() != null)
                .toList();

        double avgScore = attempts.isEmpty() ? 0.0 :
                attempts.stream().mapToDouble(a -> a.getTotalScore().doubleValue()).average().orElse(0.0);
        avgScore = Math.round(avgScore * 100.0) / 100.0;

        List<ExperimentSubmission> studentSubs = experimentSubmissionRepository.findByStudentId(studentId);
        int labsConfirmed = (int) studentSubs.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.CONFIRMED)
                .count();

        int aiSessionsCount = aiConversationRepository.findByStudentIdOrderByStartedAtDesc(studentId).size();

        DashboardDataDTO data = DashboardDataDTO.builder()
                .avgScore(avgScore)
                .completedTopics(0)
                .totalTopics(0)
                .labsConfirmed(labsConfirmed)
                .aiSessionsCount(aiSessionsCount)
                .totalExamsTaken(attempts.size())
                .lastUpdated(Instant.now())
                .build();

        DashboardSnapshot snapshot = DashboardSnapshot.builder()
                .studentId(studentId)
                .period("ALL_TIME")
                .dataJson(objectMapper.valueToTree(data))
                .generatedAt(Instant.now())
                .build();

        return mapToDTO(snapshot);
    }

    private DashboardSnapshotDTO mapToDTO(DashboardSnapshot snapshot) {
        DashboardDataDTO data = null;
        if (snapshot.getDataJson() != null) {
            try {
                data = objectMapper.treeToValue(snapshot.getDataJson(), DashboardDataDTO.class);
            } catch (Exception e) {
                log.error("Failed to parse dataJson for snapshot: {}", snapshot.getSnapshotId(), e);
            }
        }

        return DashboardSnapshotDTO.builder()
                .snapshotId(snapshot.getSnapshotId())
                .classId(snapshot.getClassId())
                .studentId(snapshot.getStudentId())
                .period(snapshot.getPeriod())
                .data(data)
                .generatedAt(snapshot.getGeneratedAt())
                .build();
    }
}