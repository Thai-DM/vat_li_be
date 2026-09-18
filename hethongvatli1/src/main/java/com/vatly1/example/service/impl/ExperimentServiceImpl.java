package com.vatly1.example.service.impl;

import com.vatly1.example.dto.request.CreateExperimentAssignmentDTO;
import com.vatly1.example.dto.request.CreateExperimentDTO;
import com.vatly1.example.dto.ExperimentAssignmentDTO;
import com.vatly1.example.dto.ExperimentDTO;
import com.vatly1.example.dto.request.GradeSubmissionDTO;
import com.vatly1.example.dto.request.SubmitExperimentDTO;
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
import com.vatly1.example.repository.ExperimentConfirmationRepository;
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

    @Override
    public List<ExperimentDTO> getExperimentsBySubject(UUID subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        return experimentRepository.findBySubjectIdOrderByOrderIndexAsc(subjectId).stream()
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

        submission.setStatus(SubmissionStatus.GRADED);
        experimentSubmissionRepository.save(submission);
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
