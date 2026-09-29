package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.ExamParticipant;
import com.vatly1.example.entity.Experiment;
import com.vatly1.example.entity.ExperimentAssignment;
import com.vatly1.example.entity.ExperimentSubmission;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.model.dto.ClassScheduleDTO;
import com.vatly1.example.model.dto.LearningMaterialDTO;
import com.vatly1.example.model.dto.StudentAgendaDTO;
import com.vatly1.example.model.dto.StudentAgendaExamDTO;
import com.vatly1.example.model.dto.StudentAgendaExperimentDTO;
import com.vatly1.example.model.dto.StudentExperimentAssignmentDTO;
import com.vatly1.example.model.dto.UpcomingTaskDTO;
import com.vatly1.example.repository.ExperimentAssignmentRepository;
import com.vatly1.example.repository.ExperimentRepository;
import com.vatly1.example.repository.ExperimentSubmissionRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamParticipantRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IClassScheduleService;
import com.vatly1.example.service.IStudentPortalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentPortalServiceImpl implements IStudentPortalService {

    private final IClassEnrollmentRepository classEnrollmentRepository;
    private final IClassRepository classRepository;
    private final TopicRepository topicRepository;
    private final LearningMaterialRepository materialRepository;
    private final ExperimentAssignmentRepository experimentAssignmentRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentSubmissionRepository experimentSubmissionRepository;
    private final IExamRepository examRepository;
    private final IExamParticipantRepository examParticipantRepository;
    private final IExamAttemptRepository examAttemptRepository;
    private final IClassScheduleService scheduleService;

    private List<UUID> getStudentClassIds(UUID studentId) {
        List<ClassEnrollment> enrollments = classEnrollmentRepository.findByStudentId(studentId);
        return enrollments.stream()
                .filter(e -> e.getStatus() == null || e.getStatus() == EnrollmentStatus.ACTIVE)
                .map(ClassEnrollment::getClassId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentExperimentAssignmentDTO> getMyExperimentAssignments(UUID studentId) {
        List<UUID> classIds = getStudentClassIds(studentId);
        List<StudentExperimentAssignmentDTO> results = new ArrayList<>();
        Instant now = Instant.now();

        for (UUID classId : classIds) {
            Class clazz = classRepository.findById(classId).orElse(null);
            List<ExperimentAssignment> assignments = experimentAssignmentRepository.findByClassId(classId);

            for (ExperimentAssignment a : assignments) {
                Experiment exp = experimentRepository.findById(a.getExperimentId()).orElse(null);
                List<ExperimentSubmission> subs = experimentSubmissionRepository.findByAssignmentIdAndStudentId(a.getAssignmentId(), studentId);
                ExperimentSubmission latestSub = subs != null && !subs.isEmpty() ? subs.get(subs.size() - 1) : null;

                boolean isOverdue = a.getDueDate() != null && now.isAfter(a.getDueDate()) && latestSub == null;

                results.add(StudentExperimentAssignmentDTO.builder()
                        .assignmentId(a.getAssignmentId())
                        .experimentId(a.getExperimentId())
                        .experimentTitle(exp != null ? exp.getTitle() : "Bài thí nghiệm")
                        .experimentDescription(exp != null ? exp.getDescription() : null)
                        .classId(a.getClassId())
                        .classCode(clazz != null ? clazz.getClassCode() : null)
                        .className(clazz != null ? clazz.getClassCode() : null)
                        .dueDate(a.getDueDate())
                        .instructionsOverride(a.getInstructionsOverride())
                        .isOverdue(isOverdue)
                        .submissionId(latestSub != null ? latestSub.getSubmissionId() : null)
                        .submissionStatus(latestSub != null ? latestSub.getStatus() : null)
                        .submittedAt(latestSub != null ? latestSub.getSubmittedAt() : null)
                        .evidenceUrl(latestSub != null ? latestSub.getEvidenceUrl() : null)
                        .fileId(latestSub != null ? latestSub.getFileId() : null)
                        .build());
            }
        }

        results.sort(Comparator.comparing(StudentExperimentAssignmentDTO::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAgendaDTO getMyAgenda(UUID studentId) {
        // 1. Schedules
        List<ClassScheduleDTO> schedules = new ArrayList<>();
        try {
            schedules = scheduleService.getStudentSchedule(studentId, null, studentId, "STUDENT");
        } catch (Exception e) {
            log.warn("Could not retrieve student schedules for agenda: {}", e.getMessage());
        }

        // 2. Exams (Class exams + Transferred exams)
        List<UUID> classIds = getStudentClassIds(studentId);
        Set<UUID> seenExamIds = new HashSet<>();
        List<StudentAgendaExamDTO> examDTOs = new ArrayList<>();
        Instant now = Instant.now();

        // Class exams
        for (UUID classId : classIds) {
            Class clazz = classRepository.findById(classId).orElse(null);
            List<Exam> exams = examRepository.findByClassId(classId);
            for (Exam ex : exams) {
                if (seenExamIds.add(ex.getExamId())) {
                    examDTOs.add(mapToAgendaExam(ex, clazz != null ? clazz.getClassCode() : null, false, studentId, now));
                }
            }
        }

        // Transferred exams
        List<ExamParticipant> transfers = examParticipantRepository.findByStudentId(studentId);
        for (ExamParticipant ep : transfers) {
            if (seenExamIds.add(ep.getExamId())) {
                Exam ex = examRepository.findById(ep.getExamId()).orElse(null);
                if (ex != null) {
                    Class clazz = classRepository.findById(ex.getClassId()).orElse(null);
                    examDTOs.add(mapToAgendaExam(ex, clazz != null ? clazz.getClassCode() : null, true, studentId, now));
                }
            }
        }

        examDTOs.sort(Comparator.comparing(StudentAgendaExamDTO::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        // 3. Experiments
        List<StudentExperimentAssignmentDTO> assignments = getMyExperimentAssignments(studentId);
        List<StudentAgendaExperimentDTO> experimentDTOs = assignments.stream()
                .map(a -> StudentAgendaExperimentDTO.builder()
                        .assignmentId(a.getAssignmentId())
                        .experimentId(a.getExperimentId())
                        .experimentTitle(a.getExperimentTitle())
                        .classId(a.getClassId())
                        .classCode(a.getClassCode())
                        .dueDate(a.getDueDate())
                        .submissionStatus(a.getSubmissionStatus() != null ? a.getSubmissionStatus().name() : "NOT_SUBMITTED")
                        .build())
                .collect(Collectors.toList());

        return StudentAgendaDTO.builder()
                .schedules(schedules)
                .exams(examDTOs)
                .experiments(experimentDTOs)
                .build();
    }

    private StudentAgendaExamDTO mapToAgendaExam(Exam ex, String classCode, boolean isTransferred, UUID studentId, Instant now) {
        String status = "UPCOMING";
        java.util.Optional<ExamAttempt> inProgress = examAttemptRepository.findFirstByExamIdAndStudentIdAndStatus(
                ex.getExamId(), studentId, AttemptStatus.IN_PROGRESS);
        if (inProgress.isPresent()) {
            status = "IN_PROGRESS";
        } else {
            long completedCount = examAttemptRepository.countByExamIdAndStudentId(ex.getExamId(), studentId);
            if (completedCount > 0 && ex.getExamType() != com.vatly1.example.entity.enums.ExamType.PRACTICE) {
                status = "SUBMITTED";
            } else if (ex.getStartTime() != null && now.isBefore(ex.getStartTime())) {
                status = "UPCOMING";
            } else if (ex.getEndTime() != null && now.isAfter(ex.getEndTime())) {
                status = "ENDED";
            } else {
                status = "OPEN";
            }
        }

        return StudentAgendaExamDTO.builder()
                .examId(ex.getExamId())
                .title(ex.getTitle())
                .classId(ex.getClassId())
                .classCode(classCode)
                .examType(ex.getExamType())
                .durationMinutes(ex.getDurationMinutes())
                .startTime(ex.getStartTime())
                .endTime(ex.getEndTime())
                .isTransferred(isTransferred)
                .status(status)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningMaterialDTO> getMyMaterials(UUID studentId, UUID classId, UUID topicId, MaterialType type) {
        List<UUID> targetClassIds;
        if (classId != null) {
            targetClassIds = List.of(classId);
        } else {
            targetClassIds = getStudentClassIds(studentId);
        }

        Set<UUID> subjectIds = new HashSet<>();
        for (UUID cid : targetClassIds) {
            classRepository.findById(cid).ifPresent(c -> {
                if (c.getSubjectId() != null) {
                    subjectIds.add(c.getSubjectId());
                }
            });
        }

        Set<UUID> targetTopicIds = new HashSet<>();
        for (UUID sid : subjectIds) {
            List<Topic> topics = topicRepository.findBySubjectIdOrderByOrderIndexAsc(sid);
            for (Topic t : topics) {
                if (topicId == null || Objects.equals(t.getTopicId(), topicId)) {
                    targetTopicIds.add(t.getTopicId());
                }
            }
        }

        List<LearningMaterialDTO> results = new ArrayList<>();
        for (UUID tid : targetTopicIds) {
            List<LearningMaterial> materials = materialRepository.findByTopicIdOrderByCreatedAtDesc(tid);
            for (LearningMaterial m : materials) {
                if (m.getApprovalStatus() == ApprovalStatus.APPROVED) {
                    if (type == null || m.getType() == type) {
                        results.add(mapToMaterialDTO(m));
                    }
                }
            }
        }

        results.sort(Comparator.comparing(LearningMaterialDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return results;
    }

    private LearningMaterialDTO mapToMaterialDTO(LearningMaterial material) {
        return LearningMaterialDTO.builder()
                .materialId(material.getMaterialId())
                .topicId(material.getTopicId())
                .fileId(material.getFileId())
                .title(material.getTitle())
                .type(material.getType())
                .fileUrl(material.getFileUrl())
                .contentText(material.getContentText())
                .version(material.getVersion())
                .approvalStatus(material.getApprovalStatus())
                .sourceCitation(material.getSourceCitation())
                .createdBy(material.getCreatedBy())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UpcomingTaskDTO> getMyUpcomingTasks(UUID studentId) {
        List<UpcomingTaskDTO> tasks = new ArrayList<>();
        Instant now = Instant.now();

        // 1. Exams
        StudentAgendaDTO agenda = getMyAgenda(studentId);
        for (StudentAgendaExamDTO exam : agenda.getExams()) {
            if (!"SUBMITTED".equals(exam.getStatus()) && !"ENDED".equals(exam.getStatus())) {
                tasks.add(UpcomingTaskDTO.builder()
                        .taskId("EXAM_" + exam.getExamId())
                        .taskType("EXAM")
                        .title("Kỳ thi: " + exam.getTitle())
                        .classCode(exam.getClassCode())
                        .deadline(exam.getEndTime() != null ? exam.getEndTime() : exam.getStartTime())
                        .status("IN_PROGRESS".equals(exam.getStatus()) ? "IN_PROGRESS" : "PENDING")
                        .priority("HIGH")
                        .actionUrl("/exams/" + exam.getExamId())
                        .build());
            }
        }

        // 2. Experiments
        List<StudentExperimentAssignmentDTO> assignments = getMyExperimentAssignments(studentId);
        for (StudentExperimentAssignmentDTO a : assignments) {
            if (a.getSubmissionStatus() == null) {
                tasks.add(UpcomingTaskDTO.builder()
                        .taskId("EXP_" + a.getAssignmentId())
                        .taskType("EXPERIMENT")
                        .title("Thí nghiệm: " + a.getExperimentTitle())
                        .classCode(a.getClassCode())
                        .courseName(a.getClassName())
                        .deadline(a.getDueDate())
                        .status(a.isOverdue() ? "OVERDUE" : "PENDING")
                        .priority(a.isOverdue() ? "HIGH" : "MEDIUM")
                        .actionUrl("/experiments/assignments/" + a.getAssignmentId())
                        .build());
            }
        }

        tasks.sort(Comparator.comparing(UpcomingTaskDTO::getDeadline, Comparator.nullsLast(Comparator.naturalOrder())));
        return tasks;
    }
}
