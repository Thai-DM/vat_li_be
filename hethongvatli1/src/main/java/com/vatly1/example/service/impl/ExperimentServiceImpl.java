package com.vatly1.example.service.impl;

import com.vatly1.example.model.request.CreateExperimentAssignmentDTO;
import com.vatly1.example.model.request.CreateExperimentDTO;
import com.vatly1.example.model.dto.ExperimentAssignmentDTO;
import com.vatly1.example.model.dto.ExperimentDTO;
import com.vatly1.example.model.request.GradeSubmissionDTO;
import com.vatly1.example.model.request.SubmitExperimentDTO;
import com.vatly1.example.entity.Experiment;
import com.vatly1.example.entity.ExperimentAssignment;
import com.vatly1.example.entity.ExperimentSubmission;
import com.vatly1.example.entity.FileUpload;
import com.vatly1.example.entity.enums.FileProcessingStatus;
import com.vatly1.example.entity.enums.SubmissionStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.ExperimentAssignmentRepository;
import com.vatly1.example.repository.ExperimentRepository;
import com.vatly1.example.repository.ExperimentSubmissionRepository;
import com.vatly1.example.repository.FileUploadRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.entity.ExperimentConfirmation;
import com.vatly1.example.entity.ExperimentRubric;
import com.vatly1.example.entity.ExperimentScore;
import com.vatly1.example.model.dto.SubmissionRubricDTO;
import com.vatly1.example.model.dto.SubmissionRubricSummaryDTO;
import com.vatly1.example.repository.ExperimentConfirmationRepository;
import com.vatly1.example.repository.ExperimentRubricRepository;
import com.vatly1.example.repository.ExperimentScoreRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.IUserProfileRepository;
import com.vatly1.example.model.dto.ExperimentSubmissionDTO;
import com.vatly1.example.service.IExperimentService;
import com.vatly1.example.service.IFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExperimentServiceImpl implements IExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentAssignmentRepository experimentAssignmentRepository;
    private final ExperimentSubmissionRepository experimentSubmissionRepository;
    private final ExperimentConfirmationRepository experimentConfirmationRepository;
    private final ISubjectRepository subjectRepository;
    private final IClassRepository classRepository;
    private final FileUploadRepository fileUploadRepository;
    private final IFileStorageService fileStorageService;
    private final IClassEnrollmentRepository enrollmentRepository;
    private final com.vatly1.example.service.INotificationService notificationService;
    private final ExperimentRubricRepository experimentRubricRepository;
    private final ExperimentScoreRepository experimentScoreRepository;
    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;

    @Override
    public List<ExperimentDTO> getExperimentsBySubject(UUID subjectId) {
        if (subjectId == null) {
            return getAllExperiments();
        }
        if (!subjectRepository.existsById(subjectId)) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        return experimentRepository.findBySubjectIdOrderByOrderIndexAsc(subjectId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExperimentDTO> getAllExperiments() {
        return experimentRepository.findAllByOrderByOrderIndexAsc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ExperimentDTO getExperimentById(UUID experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new CustomException("Experiment not found", HttpStatus.NOT_FOUND));
        return mapToDTO(experiment);
    }

    @Override
    public ExperimentDTO createExperiment(CreateExperimentDTO dto) {
        if (!subjectRepository.existsById(dto.getSubjectId())) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }

        Experiment experiment = Experiment.builder()
                .subjectId(dto.getSubjectId())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .sceneAssetUrl(dto.getSceneAssetUrl())
                .sceneAssetsJson(dto.getSceneAssetsJson())
                .instructions(dto.getInstructions())
                .orderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : 0)
                .build();

        experiment = experimentRepository.save(experiment);
        return mapToDTO(experiment);
    }

    @Override
    public ExperimentAssignmentDTO assignExperiment(UUID experimentId, CreateExperimentAssignmentDTO dto, UUID assignedBy) {
        if (!experimentRepository.existsById(experimentId)) {
            throw new CustomException("Experiment not found", HttpStatus.NOT_FOUND);
        }
        if (!classRepository.existsById(dto.getClassId())) {
            throw new CustomException("Class not found", HttpStatus.NOT_FOUND);
        }
        if (experimentAssignmentRepository.existsByExperimentIdAndClassId(experimentId, dto.getClassId())) {
            throw new CustomException("Experiment already assigned to this class", HttpStatus.BAD_REQUEST);
        }

        ExperimentAssignment assignment = ExperimentAssignment.builder()
                .experimentId(experimentId)
                .classId(dto.getClassId())
                .assignedBy(assignedBy)
                .dueDate(dto.getDueDate())
                .instructionsOverride(dto.getInstructionsOverride())
                .createdAt(Instant.now())
                .build();

        assignment = experimentAssignmentRepository.save(assignment);
        
        return ExperimentAssignmentDTO.builder()
                .assignmentId(assignment.getAssignmentId())
                .experimentId(assignment.getExperimentId())
                .classId(assignment.getClassId())
                .assignedBy(assignment.getAssignedBy())
                .dueDate(assignment.getDueDate())
                .instructionsOverride(assignment.getInstructionsOverride())
                .createdAt(assignment.getCreatedAt())
                .build();
    }

    @Override
    public void submitExperiment(UUID assignmentId, SubmitExperimentDTO dto, UUID studentId) {
        ExperimentAssignment assignment = experimentAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new CustomException("Assignment not found", HttpStatus.NOT_FOUND));

        if (!enrollmentRepository.existsByClassIdAndStudentId(assignment.getClassId(), studentId)) {
            throw new CustomException("Sinh viên không thuộc lớp học được giao bài thí nghiệm này", HttpStatus.FORBIDDEN);
        }

        ExperimentSubmission submission = ExperimentSubmission.builder()
                .assignmentId(assignmentId)
                .studentId(studentId)
                .evidenceUrl(dto.getEvidenceUrl())
                .rawDataJson(dto.getRawDataJson())
                .status(SubmissionStatus.PENDING)
                .submittedAt(Instant.now())
                .build();

        if (dto.getFile() != null && !dto.getFile().isEmpty()) {
            String fileUrl = fileStorageService.storeFile(dto.getFile());
            
            FileUpload fileUpload = FileUpload.builder()
                    .uploaderId(studentId)
                    .originalFilename(dto.getFile().getOriginalFilename())
                    .storedUrl(fileUrl)
                    .fileSizeBytes(dto.getFile().getSize())
                    .mimeType(dto.getFile().getContentType())
                    .purpose("EXPERIMENT_SUBMISSION")
                    .status(FileProcessingStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();
            
            fileUpload = fileUploadRepository.save(fileUpload);
            
            submission.setFileId(fileUpload.getFileId());
            submission.setEvidenceUrl(fileUrl);
        }

        experimentSubmissionRepository.save(submission);
    }

    @Override
    @Transactional
    public void gradeSubmission(UUID submissionId, GradeSubmissionDTO scoreDTO, UUID graderId) {
        ExperimentSubmission submission = experimentSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new CustomException("Bài nộp không tồn tại", HttpStatus.NOT_FOUND));

        if (submission.getStatus() == SubmissionStatus.CONFIRMED || experimentConfirmationRepository.existsBySubmissionId(submissionId)) {
            throw new CustomException("Bài nộp đã được xác nhận kết quả trước đó, không thể sửa đổi", HttpStatus.BAD_REQUEST);
        }

        if (scoreDTO != null && scoreDTO.getRubricId() != null) {
            if (submission.getAssignmentId() != null) {
                ExperimentAssignment assignment = experimentAssignmentRepository.findById(submission.getAssignmentId()).orElse(null);
                if (assignment != null) {
                    Experiment experiment = experimentRepository.findById(assignment.getExperimentId()).orElse(null);
                    if (experiment != null) {
                        syncRubricsFromSceneAssets(experiment);
                    }
                }
            }

            ExperimentRubric rubric = experimentRubricRepository.findById(scoreDTO.getRubricId()).orElse(null);

            if (scoreDTO.getScore() != null) {
                if (scoreDTO.getScore().compareTo(java.math.BigDecimal.ZERO) < 0) {
                    throw new CustomException("Điểm số không được nhỏ hơn 0", HttpStatus.BAD_REQUEST);
                }
                if (rubric != null && rubric.getMaxScore() != null && scoreDTO.getScore().compareTo(rubric.getMaxScore()) > 0) {
                    throw new CustomException("Điểm chấm (" + scoreDTO.getScore() + ") vượt quá điểm tối đa của tiêu chí (" + rubric.getMaxScore() + ")", HttpStatus.BAD_REQUEST);
                }
            }

            String finalComment = scoreDTO.getComment() != null ? scoreDTO.getComment() : scoreDTO.getFeedback();

            ExperimentScore score = experimentScoreRepository
                    .findBySubmissionIdAndRubricId(submissionId, scoreDTO.getRubricId())
                    .orElse(null);

            if (score == null) {
                score = ExperimentScore.builder()
                        .submissionId(submissionId)
                        .rubricId(scoreDTO.getRubricId())
                        .score(scoreDTO.getScore() != null ? scoreDTO.getScore() : java.math.BigDecimal.ZERO)
                        .graderId(graderId)
                        .gradedAt(Instant.now())
                        .comment(finalComment)
                        .build();
            } else {
                if (scoreDTO.getScore() != null) {
                    score.setScore(scoreDTO.getScore());
                }
                score.setGraderId(graderId);
                score.setGradedAt(Instant.now());
                if (finalComment != null) {
                    score.setComment(finalComment);
                }
            }
            experimentScoreRepository.save(score);
        }

        submission.setStatus(SubmissionStatus.GRADED);
        experimentSubmissionRepository.save(submission);
    }

    @Override
    public List<SubmissionRubricDTO> getSubmissionRubrics(UUID submissionId, UUID rubricId, UUID currentUserId, String currentUserRole) {
        ExperimentSubmission submission = experimentSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new CustomException("Bài nộp không tồn tại", HttpStatus.NOT_FOUND));

        if (currentUserRole != null && currentUserRole.equalsIgnoreCase("STUDENT")) {
            if (currentUserId == null || !currentUserId.equals(submission.getStudentId())) {
                throw new CustomException("Bạn không có quyền xem tiêu chí của bài nộp này", HttpStatus.FORBIDDEN);
            }
        }

        ExperimentAssignment assignment = experimentAssignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(() -> new CustomException("Không tìm thấy đợt giao bài thí nghiệm", HttpStatus.NOT_FOUND));

        Experiment experiment = experimentRepository.findById(assignment.getExperimentId())
                .orElseThrow(() -> new CustomException("Không tìm thấy bài thí nghiệm", HttpStatus.NOT_FOUND));

        syncRubricsFromSceneAssets(experiment);

        List<ExperimentRubric> rubrics = experimentRubricRepository.findByExperimentId(experiment.getExperimentId());

        List<ExperimentScore> scores = experimentScoreRepository.findBySubmissionId(submissionId);
        java.util.Map<UUID, ExperimentScore> scoreMap = scores.stream()
                .collect(Collectors.toMap(ExperimentScore::getRubricId, java.util.function.Function.identity(), (a, b) -> a));

        List<SubmissionRubricDTO> list = rubrics.stream().map(r -> {
            ExperimentScore s = scoreMap.get(r.getRubricId());
            boolean isGraded = (s != null && s.getScore() != null);
            return SubmissionRubricDTO.builder()
                    .rubricId(r.getRubricId())
                    .experimentId(r.getExperimentId())
                    .criteriaName(r.getCriteriaName())
                    .maxScore(r.getMaxScore())
                    .description(r.getDescription())
                    .submissionId(submissionId)
                    .scoreId(s != null ? s.getScoreId() : null)
                    .score(s != null ? s.getScore() : null)
                    .comment(s != null ? s.getComment() : null)
                    .feedback(s != null ? s.getComment() : null)
                    .graderId(s != null ? s.getGraderId() : null)
                    .gradedAt(s != null ? s.getGradedAt() : null)
                    .isGraded(isGraded)
                    .build();
        }).collect(Collectors.toList());

        if (rubricId != null) {
            List<SubmissionRubricDTO> filtered = list.stream()
                    .filter(dto -> dto.getRubricId().equals(rubricId))
                    .collect(Collectors.toList());
            if (filtered.isEmpty()) {
                throw new CustomException("Không tìm thấy tiêu chí Rubric với ID: " + rubricId, HttpStatus.NOT_FOUND);
            }
            return filtered;
        }

        return list;
    }

    @Override
    public SubmissionRubricDTO getSubmissionRubricById(UUID submissionId, UUID rubricId, UUID currentUserId, String currentUserRole) {
        if (rubricId == null) {
            throw new CustomException("rubricId không được để trống", HttpStatus.BAD_REQUEST);
        }
        List<SubmissionRubricDTO> list = getSubmissionRubrics(submissionId, rubricId, currentUserId, currentUserRole);
        if (list.isEmpty()) {
            throw new CustomException("Không tìm thấy tiêu chí Rubric với ID: " + rubricId, HttpStatus.NOT_FOUND);
        }
        return list.get(0);
    }

    @Override
    public SubmissionRubricSummaryDTO getSubmissionRubricSummary(UUID submissionId, UUID currentUserId, String currentUserRole) {
        ExperimentSubmission submission = experimentSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new CustomException("Bài nộp không tồn tại", HttpStatus.NOT_FOUND));

        ExperimentAssignment assignment = experimentAssignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(() -> new CustomException("Không tìm thấy đợt giao bài thí nghiệm", HttpStatus.NOT_FOUND));

        Experiment experiment = experimentRepository.findById(assignment.getExperimentId())
                .orElseThrow(() -> new CustomException("Không tìm thấy bài thí nghiệm", HttpStatus.NOT_FOUND));

        List<SubmissionRubricDTO> rubrics = getSubmissionRubrics(submissionId, null, currentUserId, currentUserRole);

        java.math.BigDecimal totalScore = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalMaxScore = java.math.BigDecimal.ZERO;
        for (SubmissionRubricDTO r : rubrics) {
            if (r.getMaxScore() != null) {
                totalMaxScore = totalMaxScore.add(r.getMaxScore());
            }
            if (r.getScore() != null) {
                totalScore = totalScore.add(r.getScore());
            }
        }

        return SubmissionRubricSummaryDTO.builder()
                .submissionId(submissionId)
                .experimentId(experiment.getExperimentId())
                .experimentTitle(experiment.getTitle())
                .studentId(submission.getStudentId())
                .status(submission.getStatus() != null ? submission.getStatus().name() : null)
                .totalScore(totalScore)
                .totalMaxScore(totalMaxScore)
                .rubrics(rubrics)
                .build();
    }

    @Override
    public List<ExperimentRubric> getRubricsByExperimentId(UUID experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new CustomException("Không tìm thấy bài thí nghiệm", HttpStatus.NOT_FOUND));
        syncRubricsFromSceneAssets(experiment);
        return experimentRubricRepository.findByExperimentId(experimentId);
    }

    private void syncRubricsFromSceneAssets(Experiment experiment) {
        if (experiment == null || experiment.getExperimentId() == null) return;
        if (!experimentRubricRepository.findByExperimentId(experiment.getExperimentId()).isEmpty()) return;
        if (experiment.getSceneAssetsJson() != null && experiment.getSceneAssetsJson().has("rubric")) {
            com.fasterxml.jackson.databind.JsonNode rubricArray = experiment.getSceneAssetsJson().get("rubric");
            if (rubricArray != null && rubricArray.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : rubricArray) {
                    String criteria = node.has("criteria") ? node.get("criteria").asText() : "";
                    java.math.BigDecimal maxScore = node.has("max_score") ? java.math.BigDecimal.valueOf(node.get("max_score").asDouble()) : java.math.BigDecimal.TEN;
                    String desc = node.has("description") ? node.get("description").asText() : criteria;
                    experimentRubricRepository.save(ExperimentRubric.builder()
                            .experimentId(experiment.getExperimentId())
                            .criteriaName(criteria)
                            .maxScore(maxScore)
                            .description(desc)
                            .build());
                }
            }
        }
    }

    @Override
    @Transactional
    public void confirmSubmission(UUID submissionId, String note, UUID instructorId) {
        ExperimentSubmission submission = experimentSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new CustomException("Bài nộp không tồn tại", HttpStatus.NOT_FOUND));

        if (submission.getStatus() == SubmissionStatus.CONFIRMED || experimentConfirmationRepository.existsBySubmissionId(submissionId)) {
            throw new CustomException("Bài nộp đã được xác nhận kết quả trước đó, không thể sửa đổi", HttpStatus.BAD_REQUEST);
        }

        submission.setStatus(SubmissionStatus.CONFIRMED);
        experimentSubmissionRepository.save(submission);

        ExperimentConfirmation confirmation = ExperimentConfirmation.builder()
                .submissionId(submissionId)
                .instructorId(instructorId)
                .confirmedAt(Instant.now())
                .note(note)
                .build();
        try {
            experimentConfirmationRepository.saveAndFlush(confirmation);

            notificationService.sendNotification(
                    submission.getStudentId(),
                    "Kết quả bài nộp thí nghiệm",
                    "Bài nộp thí nghiệm của bạn đã được giảng viên chấm và xác nhận kết quả.",
                    com.vatly1.example.entity.enums.NotificationType.EXPERIMENT_GRADED,
                    submissionId,
                    "EXPERIMENT_SUBMISSION"
            );
        } catch (DataIntegrityViolationException e) {
            throw new CustomException("Bài nộp đã được xác nhận kết quả trước đó, không thể sửa đổi", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public List<ExperimentSubmissionDTO> getSubmissions(
            UUID assignmentId, UUID experimentId, UUID classId, UUID studentId,
            UUID instructorId, Boolean myClassesOnly,
            SubmissionStatus status,
            UUID currentUserId, String currentUserRole) {

        // Phân quyền: Nếu là STUDENT, chỉ được xem bài nộp của chính mình
        if (currentUserRole != null && currentUserRole.equalsIgnoreCase("STUDENT")) {
            if (studentId != null && !studentId.equals(currentUserId)) {
                throw new CustomException("Bạn không có quyền xem bài nộp của sinh viên khác", HttpStatus.FORBIDDEN);
            }
            studentId = currentUserId;
        }

        // Lọc theo giảng viên:
        // Nếu instructorId được truyền, hoặc myClassesOnly=true, hoặc người gọi là INSTRUCTOR và không chọn lớp/bài giao cụ thể
        UUID targetInstructorId = instructorId;
        if (targetInstructorId == null && Boolean.TRUE.equals(myClassesOnly)) {
            targetInstructorId = currentUserId;
        }
        if (targetInstructorId == null && currentUserRole != null && currentUserRole.equalsIgnoreCase("INSTRUCTOR")
                && assignmentId == null && classId == null && experimentId == null && studentId == null
                && !Boolean.FALSE.equals(myClassesOnly)) {
            targetInstructorId = currentUserId;
        }

        List<ExperimentSubmission> list;

        if (assignmentId != null) {
            list = experimentSubmissionRepository.findByAssignmentId(assignmentId);
        } else if (classId != null) {
            List<ExperimentAssignment> assignments = experimentAssignmentRepository.findByClassId(classId);
            if (assignments.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            List<UUID> assignIds = assignments.stream().map(ExperimentAssignment::getAssignmentId).collect(Collectors.toList());
            list = experimentSubmissionRepository.findByAssignmentIdIn(assignIds);
        } else if (targetInstructorId != null) {
            List<UUID> classIds = classRepository.findClassIdsByInstructorIdOrStaffUserId(targetInstructorId);
            if (classIds.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            List<ExperimentAssignment> assignments = experimentAssignmentRepository.findByClassIdIn(classIds);
            if (assignments.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            List<UUID> assignIds = assignments.stream().map(ExperimentAssignment::getAssignmentId).collect(Collectors.toList());
            list = experimentSubmissionRepository.findByAssignmentIdIn(assignIds);
        } else if (experimentId != null) {
            List<ExperimentAssignment> assignments = experimentAssignmentRepository.findByExperimentId(experimentId);
            if (assignments.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            List<UUID> assignIds = assignments.stream().map(ExperimentAssignment::getAssignmentId).collect(Collectors.toList());
            list = experimentSubmissionRepository.findByAssignmentIdIn(assignIds);
        } else if (studentId != null) {
            list = experimentSubmissionRepository.findByStudentId(studentId);
        } else if (status != null) {
            list = experimentSubmissionRepository.findByStatus(status);
        } else {
            list = experimentSubmissionRepository.findAll();
        }

        // Áp dụng các bộ lọc kết hợp bổ sung nếu truyền nhiều param cùng lúc
        if (experimentId != null && (classId != null || targetInstructorId != null)) {
            List<ExperimentAssignment> expAssignments = experimentAssignmentRepository.findByExperimentId(experimentId);
            java.util.Set<UUID> expAssignIds = expAssignments.stream().map(ExperimentAssignment::getAssignmentId).collect(Collectors.toSet());
            list = list.stream().filter(s -> expAssignIds.contains(s.getAssignmentId())).collect(Collectors.toList());
        }
        if (studentId != null) {
            UUID finalStudentId = studentId;
            list = list.stream().filter(s -> finalStudentId.equals(s.getStudentId())).collect(Collectors.toList());
        }
        if (status != null) {
            list = list.stream().filter(s -> status == s.getStatus()).collect(Collectors.toList());
        }
        if (assignmentId != null) {
            UUID finalAssignId = assignmentId;
            list = list.stream().filter(s -> finalAssignId.equals(s.getAssignmentId())).collect(Collectors.toList());
        }

        // Sắp xếp bài nộp mới nhất lên đầu
        list.sort(java.util.Comparator.comparing(
                ExperimentSubmission::getSubmittedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())
        ));

        return list.stream()
                .map(sub -> mapToSubmissionDTO(sub, false, currentUserId, currentUserRole))
                .collect(Collectors.toList());
    }

    @Override
    public ExperimentSubmissionDTO getSubmissionById(UUID submissionId, UUID currentUserId, String currentUserRole) {
        ExperimentSubmission submission = experimentSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new CustomException("Bài nộp không tồn tại", HttpStatus.NOT_FOUND));

        if (currentUserRole != null && currentUserRole.equalsIgnoreCase("STUDENT")) {
            if (currentUserId == null || !currentUserId.equals(submission.getStudentId())) {
                throw new CustomException("Bạn không có quyền xem bài nộp này", HttpStatus.FORBIDDEN);
            }
        }

        return mapToSubmissionDTO(submission, true, currentUserId, currentUserRole);
    }

    private ExperimentSubmissionDTO mapToSubmissionDTO(
            ExperimentSubmission submission, boolean includeRubrics,
            UUID currentUserId, String currentUserRole) {

        ExperimentSubmissionDTO dto = ExperimentSubmissionDTO.builder()
                .submissionId(submission.getSubmissionId())
                .assignmentId(submission.getAssignmentId())
                .studentId(submission.getStudentId())
                .submittedAt(submission.getSubmittedAt())
                .evidenceUrl(submission.getEvidenceUrl())
                .fileId(submission.getFileId())
                .rawDataJson(submission.getRawDataJson())
                .status(submission.getStatus())
                .build();

        // Thông tin sinh viên
        if (submission.getStudentId() != null) {
            userRepository.findById(submission.getStudentId()).ifPresent(u -> {
                dto.setStudentUsername(u.getUsername());
                dto.setStudentEmail(u.getEmail());
            });
            userProfileRepository.findById(submission.getStudentId()).ifPresent(p -> {
                dto.setStudentFullName(p.getFullName());
                dto.setStudentCode(p.getStudentCode());
            });
        }

        // Thông tin bài giao, lớp học, thí nghiệm
        if (submission.getAssignmentId() != null) {
            experimentAssignmentRepository.findById(submission.getAssignmentId()).ifPresent(assign -> {
                dto.setClassId(assign.getClassId());
                dto.setDueDate(assign.getDueDate());
                dto.setExperimentId(assign.getExperimentId());

                if (assign.getClassId() != null) {
                    classRepository.findById(assign.getClassId()).ifPresent(c -> {
                        dto.setClassCode(c.getClassCode());
                    });
                }

                if (assign.getExperimentId() != null) {
                    experimentRepository.findById(assign.getExperimentId()).ifPresent(exp -> {
                        dto.setExperimentTitle(exp.getTitle());
                    });
                }
            });
        }

        // Điểm số và số lượng Rubric
        List<ExperimentScore> scores = experimentScoreRepository.findBySubmissionId(submission.getSubmissionId());
        java.math.BigDecimal totalScore = java.math.BigDecimal.ZERO;
        int gradedCount = 0;
        for (ExperimentScore s : scores) {
            if (s.getScore() != null) {
                totalScore = totalScore.add(s.getScore());
                gradedCount++;
            }
        }
        dto.setTotalScore(scores.isEmpty() ? null : totalScore);
        dto.setGradedRubricCount(gradedCount);
        dto.setIsGraded(submission.getStatus() == SubmissionStatus.GRADED
                || submission.getStatus() == SubmissionStatus.CONFIRMED
                || !scores.isEmpty());

        if (dto.getExperimentId() != null) {
            List<ExperimentRubric> rubrics = experimentRubricRepository.findByExperimentId(dto.getExperimentId());
            dto.setTotalRubricCount(rubrics.size());
            java.math.BigDecimal maxScore = rubrics.stream()
                    .map(ExperimentRubric::getMaxScore)
                    .filter(java.util.Objects::nonNull)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            dto.setTotalMaxScore(maxScore);
        }

        // Thông tin xác nhận kết quả
        experimentConfirmationRepository.findBySubmissionId(submission.getSubmissionId()).ifPresent(conf -> {
            dto.setIsConfirmed(true);
            dto.setConfirmedBy(conf.getInstructorId());
            dto.setConfirmedAt(conf.getConfirmedAt());
            dto.setConfirmNote(conf.getNote());
        });
        if (dto.getIsConfirmed() == null) {
            dto.setIsConfirmed(submission.getStatus() == SubmissionStatus.CONFIRMED);
        }

        if (includeRubrics) {
            try {
                dto.setRubrics(getSubmissionRubrics(submission.getSubmissionId(), null, currentUserId, currentUserRole));
            } catch (Exception ignored) {
                dto.setRubrics(java.util.Collections.emptyList());
            }
        }

        return dto;
    }

    private ExperimentDTO mapToDTO(Experiment experiment) {
        return ExperimentDTO.builder()
                .experimentId(experiment.getExperimentId())
                .subjectId(experiment.getSubjectId())
                .title(experiment.getTitle())
                .description(experiment.getDescription())
                .sceneAssetUrl(experiment.getSceneAssetUrl())
                .sceneAssetsJson(experiment.getSceneAssetsJson())
                .instructions(experiment.getInstructions())
                .orderIndex(experiment.getOrderIndex())
                .build();
    }
}