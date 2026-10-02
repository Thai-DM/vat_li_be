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