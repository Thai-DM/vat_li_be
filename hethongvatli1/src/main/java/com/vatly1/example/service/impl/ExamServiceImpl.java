package com.vatly1.example.service.impl;

import com.vatly1.example.model.request.AddExamQuestionDTO;
import com.vatly1.example.model.request.CreateExamDTO;
import com.vatly1.example.model.dto.BatchSaveResultDTO;
import com.vatly1.example.model.dto.ExamAttemptDTO;
import com.vatly1.example.model.dto.ExamAttemptPolicyDTO;
import com.vatly1.example.model.dto.ExamAttemptProgressDTO;
import com.vatly1.example.model.dto.ExamDTO;
import com.vatly1.example.model.request.BatchSubmitAnswerDTO;
import com.vatly1.example.model.request.SubmitAnswerDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.ExamAnswer;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.QuestionOption;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.MatrixStatus;
import com.vatly1.example.model.request.BulkProvisionFinalExamDTO;
import com.vatly1.example.model.response.BulkProvisionFinalExamResultDTO;
import com.vatly1.example.model.dto.ExamAttemptSummaryDTO;
import com.vatly1.example.model.dto.ExamQuestionDetailDTO;
import com.vatly1.example.model.dto.QuestionOptionDTO;
import com.vatly1.example.model.dto.StudentExamQuestionDTO;
import com.vatly1.example.model.dto.StudentQuestionOptionDTO;
import com.vatly1.example.model.request.GradeAttemptDTO;
import com.vatly1.example.model.request.UpdateExamDTO;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.IExamAnswerRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamMatrixDetailRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.IUserProfileRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.QuestionAcceptedAnswer;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.QuestionAcceptedAnswerRepository;
import com.vatly1.example.utils.AnswerNormalizationUtil;
import com.vatly1.example.entity.ExamViolationLog;
import com.vatly1.example.entity.enums.ProctoringMode;
import com.vatly1.example.entity.enums.ViolationSeverity;
import com.vatly1.example.entity.enums.ViolationType;
import com.vatly1.example.model.dto.ExamViolationLogDTO;
import com.vatly1.example.model.dto.ViolationResultDTO;
import com.vatly1.example.model.request.DisqualifyAttemptRequestDTO;
import com.vatly1.example.model.request.LogViolationRequestDTO;
import com.vatly1.example.repository.IExamViolationLogRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.vatly1.example.entity.enums.ClassStaffRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamServiceImpl implements IExamService {

    private final IExamRepository examRepository;
    private final IExamQuestionRepository examQuestionRepository;
    private final IExamAttemptRepository examAttemptRepository;
    private final IExamAnswerRepository examAnswerRepository;
    private final IExamMatrixDetailRepository examMatrixDetailRepository;
    private final IClassRepository classRepository;
    private final IClassEnrollmentRepository classEnrollmentRepository;
    private final IClassStaffRepository classStaffRepository;
    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final TopicRepository topicRepository;
    private final IChapterRepository chapterRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionAcceptedAnswerRepository questionAcceptedAnswerRepository;
    private final com.vatly1.example.service.ISystemSettingService systemSettingService;
    private final com.vatly1.example.service.INotificationService notificationService;
    private final com.vatly1.example.repository.IExamParticipantRepository examParticipantRepository;
    private final com.vatly1.example.repository.IExamMatrixRepository examMatrixRepository;
    private final com.vatly1.example.repository.IQuestionCloRepository questionCloRepository;
    private final com.vatly1.example.service.algorithm.CloAwareQuestionMatcher cloAwareQuestionMatcher;
    private final com.vatly1.example.service.IClassSettingsService classSettingsService;
    private final IExamViolationLogRepository examViolationLogRepository;

    @Override
    @Transactional
    public ExamDTO createExam(CreateExamDTO dto, UUID creatorId) {
        Class clazz = classRepository.findById(dto.getClassId())
                .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));

        User currentUser = resolveCurrentUser(creatorId);
        validateExamManagePermission(clazz, currentUser, dto.getExamType());

        if (dto.getDurationMinutes() == null || dto.getDurationMinutes() <= 0) {
            throw new CustomException("Thời lượng làm bài phải lớn hơn 0", HttpStatus.BAD_REQUEST);
        }

        if (dto.getStartTime() != null && dto.getEndTime() != null && dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new CustomException("Thời gian bắt đầu không được sau thời gian kết thúc", HttpStatus.BAD_REQUEST);
        }

        if (dto.getMatrixId() != null) {
            ExamMatrix matrix = examMatrixRepository.findById(dto.getMatrixId())
                    .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi", HttpStatus.NOT_FOUND));
            if (!Objects.equals(matrix.getSubjectId(), clazz.getSubjectId())) {
                throw new CustomException("Ma trận đề thi không thuộc môn học của lớp học này", HttpStatus.BAD_REQUEST);
            }
            if (currentUser.getRole() != UserRole.ADMIN && matrix.getExamType() == com.vatly1.example.entity.enums.ExamType.FINAL) {
                throw new CustomException("Giảng viên không được phép sử dụng ma trận đề thi cuối kỳ (FINAL)", HttpStatus.FORBIDDEN);
            }
        }

        Exam exam = Exam.builder()
                .classId(dto.getClassId())
                .matrixId(dto.getMatrixId())
                .title(dto.getTitle().trim())
                .examType(dto.getExamType())
                .durationMinutes(dto.getDurationMinutes())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .createdBy(currentUser.getUserId())
                .createdAt(Instant.now())
                .proctoringMode(dto.getProctoringMode() != null ? dto.getProctoringMode() : ProctoringMode.NONE)
                .maxViolationsAllowed(dto.getMaxViolationsAllowed() != null ? dto.getMaxViolationsAllowed() : 5)
                .autoBanOnLimit(Boolean.TRUE.equals(dto.getAutoBanOnLimit()))
                .isPublished(dto.getIsPublished() != null ? dto.getIsPublished() : true)
                .build();

        Exam savedExam = examRepository.save(exam);

        if (dto.getMatrixId() != null) {
            populateQuestionsFromMatrix(savedExam.getExamId(), dto.getMatrixId());
        }

        if (Boolean.TRUE.equals(savedExam.getIsPublished())) {
            try {
                notificationService.sendNotificationToClass(
                        savedExam.getClassId(),
                        "Bài thi mới: " + savedExam.getTitle(),
                        "Lớp học vừa có bài thi mới: " + savedExam.getTitle() + " (Thời lượng: " + savedExam.getDurationMinutes() + " phút). Vui lòng hoàn thành đúng thời hạn.",
                        com.vatly1.example.entity.enums.NotificationType.EXAM_NEW,
                        savedExam.getExamId(),
                        "EXAM"
                );
            } catch (Exception e) {
                log.warn("Failed to send exam notification: {}", e.getMessage());
            }
        }

        return toExamDTO(savedExam);
    }

    @Override
    @Transactional
    public int autoGenerateQuestions(UUID examId, UUID instructorId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        checkExamOwnership(exam, instructorId);

        if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.FINAL) {
            User user = userRepository.findById(instructorId).orElse(null);
            boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
            if (!isAdmin) {
                throw new CustomException("Chỉ Quản trị viên (ADMIN) mới có quyền tự động sinh câu hỏi cho kỳ thi cuối kỳ (FINAL)", HttpStatus.FORBIDDEN);
            }
        }

        if (examAttemptRepository.existsByExamId(examId)) {
            throw new CustomException("Cannot auto-generate questions for an exam that already has student attempts", HttpStatus.BAD_REQUEST);
        }

        if (exam.getEndTime() != null && Instant.now().isAfter(exam.getEndTime())) {
            throw new CustomException("Cannot auto-generate questions for an exam that has already ended", HttpStatus.BAD_REQUEST);
        }

        if (exam.getMatrixId() == null) {
            throw new CustomException("Exam has no associated matrix", HttpStatus.BAD_REQUEST);
        }

        return populateQuestionsFromMatrix(examId, exam.getMatrixId());
    }

    private int populateQuestionsFromMatrix(UUID examId, UUID matrixId) {
        Exam exam = examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if (examAttemptRepository.existsByExamId(examId)) {
            throw new CustomException("Cannot auto-generate questions for an exam that already has student attempts", HttpStatus.BAD_REQUEST);
        }

        if (exam.getEndTime() != null && Instant.now().isAfter(exam.getEndTime())) {
            throw new CustomException("Cannot auto-generate questions for an exam that has already ended", HttpStatus.BAD_REQUEST);
        }

        ExamMatrix matrix = examMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi", HttpStatus.NOT_FOUND));

        Class clazz = classRepository.findById(exam.getClassId())
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học của bài thi", HttpStatus.NOT_FOUND));

        if (!Objects.equals(matrix.getSubjectId(), clazz.getSubjectId())) {
            throw new CustomException("Ma trận đề thi không thuộc môn học của lớp học này", HttpStatus.BAD_REQUEST);
        }

        if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.FINAL) {
            return populateFinalExamQuestions(exam, matrix);
        } else {
            return populatePracticeExamQuestions(examId, matrixId);
        }
    }

    private int populateFinalExamQuestions(Exam exam, ExamMatrix matrix) {
        if (matrix.getExamType() != com.vatly1.example.entity.enums.ExamType.FINAL) {
            throw new CustomException("Bài thi cuối kỳ (FINAL) chỉ được sử dụng ma trận đề thi loại FINAL", HttpStatus.BAD_REQUEST);
        }

        if (matrix.getStatus() != com.vatly1.example.entity.enums.MatrixStatus.PUBLISHED) {
            throw new CustomException("Ma trận đề thi cuối kỳ phải ở trạng thái PUBLISHED mới được sử dụng để sinh đề thi", HttpStatus.BAD_REQUEST);
        }

        if (examQuestionRepository.countByExamId(exam.getExamId()) > 0) {
            throw new CustomException("Bài thi đã có danh sách câu hỏi, không được phép sinh lại đề thi", HttpStatus.BAD_REQUEST);
        }

        List<ExamMatrixDetail> details = examMatrixDetailRepository.findByMatrixId(matrix.getMatrixId());
        if (details == null || details.isEmpty()) {
            throw new CustomException("Ma trận đề thi không có cấu hình chi tiết (details)", HttpStatus.BAD_REQUEST);
        }

        int totalRequired = 0;
        for (ExamMatrixDetail d : details) {
            if (d.getNumQuestions() != null && d.getNumQuestions() < 0) {
                throw new CustomException("Số lượng câu hỏi trong ma trận không được âm", HttpStatus.BAD_REQUEST);
            }
            totalRequired += (d.getNumQuestions() != null ? d.getNumQuestions() : 0);
        }

        if (totalRequired <= 0) {
            throw new CustomException("Ma trận đề thi yêu cầu tổng số câu hỏi phải lớn hơn 0", HttpStatus.BAD_REQUEST);
        }

        Set<UUID> topicIds = details.stream()
                .map(ExamMatrixDetail::getTopicId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<QuestionBank> candidates = topicIds.isEmpty() ? Collections.emptyList() :
                questionBankRepository.findBySubjectIdAndTopicIdInAndApprovalStatus(
                        matrix.getSubjectId(), topicIds, ApprovalStatus.APPROVED)
                        .stream()
                        .filter(q -> q.getQuestionType() == QuestionType.MCQ_SINGLE || q.getQuestionType() == QuestionType.FILL_BLANK)
                        .collect(Collectors.toList());

        Set<UUID> candidateIds = candidates.stream()
                .map(QuestionBank::getQuestionId)
                .collect(Collectors.toSet());

        List<QuestionClo> questionClos = candidateIds.isEmpty() ? Collections.emptyList() :
                questionCloRepository.findBySubjectIdAndQuestionIdIn(matrix.getSubjectId(), candidateIds);

        Map<UUID, Set<UUID>> questionCloMap = new HashMap<>();
        for (QuestionClo qc : questionClos) {
            questionCloMap.computeIfAbsent(qc.getQuestionId(), k -> new HashSet<>()).add(qc.getCloId());
        }

        com.vatly1.example.service.algorithm.CloAwareQuestionMatcher.MatchResult matchResult =
                cloAwareQuestionMatcher.match(details, candidates, questionCloMap, null);

        if (!matchResult.isFeasible()) {
            String diagMsg = matchResult.getDiagnostics().isEmpty() ?
                    String.format("Yêu cầu %d câu nhưng chỉ phân bổ được %d câu", matchResult.getTotalRequiredQuestions(), matchResult.getTotalMatchedQuestions()) :
                    String.join("; ", matchResult.getDiagnostics());
            log.warn("FINAL Exam question generation failed for examId {}: {}", exam.getExamId(), diagMsg);
            throw new CustomException("Không thể sinh đề thi cuối kỳ: " + diagMsg, HttpStatus.BAD_REQUEST);
        }

        int orderIndex = 0;
        int addedCount = 0;
        Set<UUID> chosenQuestionIds = new HashSet<>();

        for (ExamMatrixDetail detail : details) {
            List<QuestionBank> assigned = matchResult.getAssignedQuestions(detail);
            BigDecimal weight = detail.getWeightPercent() != null ? detail.getWeightPercent() : BigDecimal.ONE;

            for (QuestionBank q : assigned) {
                if (chosenQuestionIds.contains(q.getQuestionId())) {
                    throw new CustomException("Phát hiện trùng lặp câu hỏi (Question ID: " + q.getQuestionId() + ") trong quá trình sinh đề thi", HttpStatus.INTERNAL_SERVER_ERROR);
                }
                chosenQuestionIds.add(q.getQuestionId());
                orderIndex++;

                ExamQuestion eq = ExamQuestion.builder()
                        .examId(exam.getExamId())
                        .questionId(q.getQuestionId())
                        .orderIndex(orderIndex)
                        .scoreWeight(weight)
                        .build();
                examQuestionRepository.save(eq);
                addedCount++;
            }
        }

        if (addedCount != matchResult.getTotalRequiredQuestions()) {
            throw new CustomException("Lỗi lưu trữ câu hỏi đề thi: số lượng câu hỏi lưu (" + addedCount + ") không khớp với ma trận (" + matchResult.getTotalRequiredQuestions() + ")", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("FINAL Exam {} questions populated successfully: {} questions from matrix {}",
                exam.getExamId(), addedCount, matrix.getMatrixId());
        return addedCount;
    }

    private int populatePracticeExamQuestions(UUID examId, UUID matrixId) {
        List<ExamMatrixDetail> details = examMatrixDetailRepository.findByMatrixId(matrixId);
        if (details == null || details.isEmpty()) {
            throw new CustomException("Ma trận đề thi không có cấu hình chi tiết (details)", HttpStatus.BAD_REQUEST);
        }

        // Track questions already in the exam to avoid duplicates across matrix details or previous generations
        Set<UUID> chosenQuestionIds = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(examId)
                .stream()
                .map(ExamQuestion::getQuestionId)
                .collect(Collectors.toSet());

        int orderIndex = (int) examQuestionRepository.countByExamId(examId);
        int addedCount = 0;

        for (ExamMatrixDetail detail : details) {
            int requiredCount = detail.getNumQuestions() != null ? detail.getNumQuestions() : 0;
            if (requiredCount <= 0) {
                continue;
            }

            // 1. Primary query: strictly APPROVED with matching topic and difficulty
            List<QuestionBank> primaryCandidates = questionBankRepository.findByTopicIdAndDifficultyLevelAndApprovalStatus(
                    detail.getTopicId(), detail.getDifficultyLevel(), ApprovalStatus.APPROVED)
                    .stream()
                    .filter(q -> q.getQuestionType() == QuestionType.MCQ_SINGLE || q.getQuestionType() == QuestionType.FILL_BLANK)
                    .filter(q -> !chosenQuestionIds.contains(q.getQuestionId()))
                    .collect(Collectors.toList());

            List<QuestionBank> selectedForDetail = new ArrayList<>();

            if (primaryCandidates.size() >= requiredCount) {
                Collections.shuffle(primaryCandidates);
                selectedForDetail.addAll(primaryCandidates.subList(0, requiredCount));
            } else {
                // Take all available primary candidates
                selectedForDetail.addAll(primaryCandidates);
                int neededMore = requiredCount - selectedForDetail.size();

                // 2. Fallback query: strictly APPROVED within the same topic (other difficulty levels)
                Set<UUID> currentSelectedIds = selectedForDetail.stream()
                        .map(QuestionBank::getQuestionId)
                        .collect(Collectors.toSet());

                List<QuestionBank> fallbackCandidates = questionBankRepository.findByTopicIdAndApprovalStatus(
                        detail.getTopicId(), ApprovalStatus.APPROVED)
                        .stream()
                        .filter(q -> q.getQuestionType() == QuestionType.MCQ_SINGLE || q.getQuestionType() == QuestionType.FILL_BLANK)
                        .filter(q -> !chosenQuestionIds.contains(q.getQuestionId()) && !currentSelectedIds.contains(q.getQuestionId()))
                        .collect(Collectors.toList());

                if (fallbackCandidates.size() >= neededMore) {
                    Collections.shuffle(fallbackCandidates);
                    selectedForDetail.addAll(fallbackCandidates.subList(0, neededMore));
                    Topic topic = topicRepository.findById(detail.getTopicId()).orElse(null);
                    String topicName = topic != null ? topic.getTopicName() : detail.getTopicId().toString();
                    log.warn("Bài thi ID {}: Chủ đề '{}' thiếu {} câu mức độ {} (chỉ có {} câu đúng độ khó đã duyệt). Tự động bù {} câu APPROVED từ độ khó khác trong cùng chủ đề.",
                            examId, topicName, neededMore, detail.getDifficultyLevel(), primaryCandidates.size(), neededMore);
                } else {
                    // Insufficient APPROVED questions to fulfill matrix requirement
                    Topic topic = topicRepository.findById(detail.getTopicId()).orElse(null);
                    String topicName = topic != null ? topic.getTopicName() : detail.getTopicId().toString();
                    int totalAvailable = selectedForDetail.size() + fallbackCandidates.size();

                    throw new CustomException(
                            String.format("Không đủ câu hỏi đã được phê duyệt (APPROVED) cho chủ đề '%s' (mức độ %s). Yêu cầu: %d câu, khả dụng: %d câu.",
                                    topicName, detail.getDifficultyLevel(), requiredCount, totalAvailable),
                            HttpStatus.BAD_REQUEST
                    );
                }
            }

            BigDecimal weight = detail.getWeightPercent() != null ? detail.getWeightPercent() : BigDecimal.ONE;

            for (QuestionBank q : selectedForDetail) {
                chosenQuestionIds.add(q.getQuestionId());
                ExamQuestion eq = ExamQuestion.builder()
                        .examId(examId)
                        .questionId(q.getQuestionId())
                        .orderIndex(++orderIndex)
                        .scoreWeight(weight)
                        .build();
                examQuestionRepository.save(eq);
                addedCount++;
            }
        }
        return addedCount;
    }

    @Override
    @Transactional
    public void addQuestionToExam(UUID examId, AddExamQuestionDTO dto, UUID instructorId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        Class clazz = checkExamOwnership(exam, instructorId);

        if (examAttemptRepository.existsByExamId(examId)) {
            throw new CustomException("Cannot modify questions for an exam that already has student attempts", HttpStatus.BAD_REQUEST);
        }

        if (exam.getEndTime() != null && Instant.now().isAfter(exam.getEndTime())) {
            throw new CustomException("Cannot modify questions for an exam that has already ended", HttpStatus.BAD_REQUEST);
        }

        QuestionBank question = questionBankRepository.findById(dto.getQuestionId())
                .orElseThrow(() -> new CustomException("Question not found in question bank", HttpStatus.NOT_FOUND));

        if (question.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new CustomException("Only APPROVED questions can be added to an exam", HttpStatus.BAD_REQUEST);
        }

        if (!Objects.equals(question.getSubjectId(), clazz.getSubjectId())) {
            throw new CustomException("Question does not belong to the same subject as the exam", HttpStatus.BAD_REQUEST);
        }

        if (question.getQuestionType() != com.vatly1.example.entity.enums.QuestionType.MCQ_SINGLE &&
                question.getQuestionType() != com.vatly1.example.entity.enums.QuestionType.FILL_BLANK) {
            throw new CustomException("Hệ thống chỉ hỗ trợ câu hỏi trắc nghiệm đơn (MCQ_SINGLE) hoặc điền khuyết (FILL_BLANK) vào đề thi", HttpStatus.BAD_REQUEST);
        }

        if (examQuestionRepository.existsByExamIdAndQuestionId(examId, dto.getQuestionId())) {
            throw new CustomException("Question already added to this exam", HttpStatus.BAD_REQUEST);
        }

        int nextOrder = dto.getOrderIndex() != null ? dto.getOrderIndex() : (int) examQuestionRepository.countByExamId(examId) + 1;
        BigDecimal weight = dto.getScoreWeight() != null ? dto.getScoreWeight() : BigDecimal.ONE;

        ExamQuestion eq = ExamQuestion.builder()
                .examId(examId)
                .questionId(dto.getQuestionId())
                .orderIndex(nextOrder)
                .scoreWeight(weight)
                .build();
        examQuestionRepository.save(eq);
    }

    @Override
    public List<ExamDTO> getExamsByClass(UUID classId, UUID currentUserId, String role) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));

        if ("STUDENT".equalsIgnoreCase(role)) {
            if (!classEnrollmentRepository.isStudentActivelyEnrolledInClass(classId, currentUserId)) {
                throw new CustomException("Access denied: You are not enrolled in this class", HttpStatus.FORBIDDEN);
            }
            return examRepository.findByClassIdAndIsPublishedTrue(classId).stream()
                    .map(this::toExamDTO)
                    .collect(Collectors.toList());
        } else if ("INSTRUCTOR".equalsIgnoreCase(role)) {
            boolean isInstructor = Objects.equals(clazz.getInstructorId(), currentUserId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(classId, currentUserId);
            if (!isInstructor && !isStaff) {
                throw new CustomException("Access denied: You are not teaching this class", HttpStatus.FORBIDDEN);
            }
        }

        return examRepository.findByClassId(classId).stream()
                .map(this::toExamDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExamDTO> getAllExams(UUID currentUserId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return examRepository.findAll().stream()
                    .map(this::toExamDTO)
                    .collect(Collectors.toList());
        } else if ("INSTRUCTOR".equalsIgnoreCase(role) || "TA".equalsIgnoreCase(role)) {
            List<UUID> classIds = classRepository.findClassIdsByInstructorIdOrStaffUserId(currentUserId);
            if (classIds.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            return examRepository.findByClassIdIn(classIds).stream()
                    .map(this::toExamDTO)
                    .collect(Collectors.toList());
        } else {
            // STUDENT: Chỉ thấy các đề thi đã công bố (isPublished = true)
            List<UUID> classIds = classRepository.findEnrolledClassIdsByStudentId(currentUserId);
            if (classIds.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            return examRepository.findByClassIdInAndIsPublishedTrue(classIds).stream()
                    .map(this::toExamDTO)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public ExamDTO getExamById(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        return toExamDTO(exam);
    }

    @Override
    public ExamDTO getExamById(UUID examId, UUID currentUserId, String role) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if ("STUDENT".equalsIgnoreCase(role)) {
            if (Boolean.FALSE.equals(exam.getIsPublished())) {
                throw new CustomException("Kỳ thi này hiện chưa được công bố", HttpStatus.FORBIDDEN);
            }
            boolean isEnrolled = classEnrollmentRepository.isStudentActivelyEnrolledInClass(exam.getClassId(), currentUserId);
            boolean isParticipant = examParticipantRepository.existsByExamIdAndStudentId(examId, currentUserId);
            if (!isEnrolled && !isParticipant) {
                throw new CustomException("Access denied: You are not enrolled in this class", HttpStatus.FORBIDDEN);
            }
        }

        return toExamDTO(exam);
    }

    @Override
    @Transactional
    public ExamAttemptDTO startAttempt(UUID examId, UUID studentId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        User studentUser = userRepository.findById(studentId).orElse(null);
        boolean isAdmin = studentUser != null && studentUser.getRole() == UserRole.ADMIN;
        if (!isAdmin) {
            boolean isEnrolled = classEnrollmentRepository.isStudentActivelyEnrolledInClass(exam.getClassId(), studentId);
            boolean isTransferred = examParticipantRepository.existsByExamIdAndStudentId(examId, studentId);
            if (!isEnrolled && !isTransferred) {
                throw new CustomException("Bạn không có tên trong danh sách ca thi này", HttpStatus.FORBIDDEN);
            }
            if (Boolean.FALSE.equals(exam.getIsPublished())) {
                throw new CustomException("Kỳ thi này hiện chưa được công bố", HttpStatus.FORBIDDEN);
            }
        }

        Instant now = Instant.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new CustomException("Exam has not started yet", HttpStatus.BAD_REQUEST);
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            throw new CustomException("Exam has already ended", HttpStatus.BAD_REQUEST);
        }

        long questionCount = examQuestionRepository.countByExamId(examId);
        if (questionCount == 0) {
            throw new CustomException("Kỳ thi chưa có câu hỏi hoặc chưa sẵn sàng để làm bài", HttpStatus.BAD_REQUEST);
        }
        if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.FINAL && exam.getMatrixId() != null) {
            List<ExamMatrixDetail> matrixDetails = examMatrixDetailRepository.findByMatrixId(exam.getMatrixId());
            int totalRequired = matrixDetails.stream()
                    .mapToInt(d -> d.getNumQuestions() != null ? d.getNumQuestions() : 0)
                    .sum();
            if (questionCount < totalRequired) {
                throw new CustomException(String.format("Kỳ thi cuối kỳ chưa có đủ số lượng câu hỏi theo ma trận đề (%d/%d câu)",
                        questionCount, totalRequired), HttpStatus.BAD_REQUEST);
            }
        }

        int nextAttemptNumber = 1;

        if (classSettingsService != null && exam.getClassId() != null) {
            com.vatly1.example.entity.ClassSettings settings = classSettingsService.getEffectiveSettings(exam.getClassId());
            if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.PRACTICE) {
                if (!Boolean.TRUE.equals(settings.getPracticeEnabled())) {
                    throw new CustomException("Hoạt động luyện tập hiện đang bị khóa đối với lớp này", HttpStatus.BAD_REQUEST);
                }
            } else if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.QUIZ) {
                if (!Boolean.TRUE.equals(settings.getQuizEnabled())) {
                    throw new CustomException("Hoạt động Quiz hiện đang bị khóa đối với lớp này", HttpStatus.BAD_REQUEST);
                }
            }
        }

        if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.PRACTICE) {
            // For practice exams: check if student has an unfinished (IN_PROGRESS) attempt
            java.util.Optional<ExamAttempt> inProgress = examAttemptRepository.findFirstByExamIdAndStudentIdAndStatus(
                    examId, studentId, AttemptStatus.IN_PROGRESS);
            if (inProgress.isPresent()) {
                throw new CustomException("You already have an attempt in progress for this practice exam", HttpStatus.CONFLICT);
            }

            long existingCount = examAttemptRepository.countByExamIdAndStudentId(examId, studentId);
            int maxAttempts = 10;
            if (classSettingsService != null && exam.getClassId() != null) {
                com.vatly1.example.entity.ClassSettings settings = classSettingsService.getEffectiveSettings(exam.getClassId());
                if (settings.getMaxPracticeAttempts() != null) {
                    maxAttempts = settings.getMaxPracticeAttempts();
                }
            } else if (systemSettingService != null) {
                try {
                    com.vatly1.example.entity.SystemSetting maxSetting = systemSettingService.getSettingByKey("exam.max_attempts");
                    if (maxSetting != null && maxSetting.getSettingValue() != null) {
                        maxAttempts = Integer.parseInt(maxSetting.getSettingValue().trim());
                    }
                } catch (Exception ignored) {}
            }
            if (existingCount >= maxAttempts) {
                throw new CustomException("Maximum practice attempts reached (" + maxAttempts + ")", HttpStatus.BAD_REQUEST);
            }
            nextAttemptNumber = (int) existingCount + 1;
        } else {
            // Official exams (MIDTERM, FINAL): Strictly 1 attempt
            if (examAttemptRepository.existsByExamIdAndStudentId(examId, studentId)) {
                throw new CustomException("Attempt already exists for this exam", HttpStatus.CONFLICT);
            }
        }

        try {
            ExamAttempt attempt = ExamAttempt.builder()
                    .examId(examId)
                    .studentId(studentId)
                    .attemptNumber(nextAttemptNumber)
                    .startedAt(now)
                    .status(AttemptStatus.IN_PROGRESS)
                    .totalScore(BigDecimal.ZERO)
                    .build();

            ExamAttempt saved = examAttemptRepository.saveAndFlush(attempt);
            ExamAttemptDTO result = toAttemptDTO(saved);
            List<StudentExamQuestionDTO> questions = buildStudentQuestionsForAttempt(saved, exam, "STUDENT");
            result.setQuestions(questions);
            result.setExamTitle(exam.getTitle());
            result.setDurationMinutes(exam.getDurationMinutes());
            result.setExamStartTime(exam.getStartTime());
            result.setExamEndTime(exam.getEndTime());
            result.setTotalQuestions(questions != null ? questions.size() : 0);
            return result;
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent exam attempt detected for student {} on exam {}", studentId, examId);
            throw new CustomException("Attempt already exists for this exam", HttpStatus.CONFLICT);
        }
    }

    @Override
    @Transactional
    public void submitAnswer(UUID attemptId, SubmitAnswerDTO dto, UUID studentId) {
        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(attempt.getStudentId(), studentId)) {
            throw new CustomException("Access denied: This attempt belongs to another student", HttpStatus.FORBIDDEN);
        }

        if (Boolean.TRUE.equals(attempt.getIsDisqualified()) || attempt.getStatus() == AttemptStatus.DISQUALIFIED) {
            throw new CustomException("Bạn đã bị giảng viên đình chỉ bài thi do vi phạm quy chế. Lý do: " +
                    (attempt.getDisqualifiedReason() != null ? attempt.getDisqualifiedReason() : "Vi phạm quy chế thi"), HttpStatus.FORBIDDEN);
        }

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new CustomException("Cannot submit answer: Attempt is already completed", HttpStatus.BAD_REQUEST);
        }

        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        if (isAttemptExpired(attempt, exam)) {
            throw new CustomException("Cannot submit answer: Attempt time has expired", HttpStatus.BAD_REQUEST);
        }

        if (!examQuestionRepository.existsByExamIdAndQuestionId(attempt.getExamId(), dto.getQuestionId())) {
            throw new CustomException("Question does not belong to this exam", HttpStatus.BAD_REQUEST);
        }

        ExamAnswer answer = examAnswerRepository.findByAttemptIdAndQuestionId(attemptId, dto.getQuestionId())
                .orElse(ExamAnswer.builder()
                        .attemptId(attemptId)
                        .questionId(dto.getQuestionId())
                        .build());

        QuestionBank qb = questionBankRepository.findById(dto.getQuestionId()).orElse(null);
        if (qb != null && qb.getQuestionType() == com.vatly1.example.entity.enums.QuestionType.FILL_BLANK) {
            answer.setAnswerText(dto.getAnswerText());
            answer.setSelectedOptionIds(new ArrayList<>());
        } else {
            answer.setSelectedOptionIds(dto.getSelectedOptionIds() != null ? new ArrayList<>(dto.getSelectedOptionIds()) : new ArrayList<>());
            answer.setAnswerText(null);
        }
        examAnswerRepository.save(answer);
    }

    private Instant calculateAttemptExpiry(ExamAttempt attempt, Exam exam) {
        if (attempt == null || exam == null) {
            return null;
        }
        Instant startedAt = attempt.getStartedAt();
        Integer durationMinutes = exam.getDurationMinutes();
        Instant expiry = null;
        if (startedAt != null && durationMinutes != null && durationMinutes > 0) {
            expiry = startedAt.plus(durationMinutes, ChronoUnit.MINUTES);
        }
        if (exam.getEndTime() != null) {
            if (expiry == null || exam.getEndTime().isBefore(expiry)) {
                expiry = exam.getEndTime();
            }
        }
        return expiry;
    }

    private boolean isAttemptExpired(ExamAttempt attempt, Exam exam) {
        Instant expiry = calculateAttemptExpiry(attempt, exam);
        return expiry != null && Instant.now().isAfter(expiry);
    }

    private BigDecimal calculateAndGradeAttemptAnswers(UUID attemptId, UUID examId) {
        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(examId);
        List<ExamAnswer> answers = examAnswerRepository.findByAttemptId(attemptId);
        Map<UUID, ExamAnswer> answerMap = answers.stream()
                .collect(Collectors.toMap(ExamAnswer::getQuestionId, a -> a, (a1, a2) -> a1));

        BigDecimal totalScore = BigDecimal.ZERO;

        for (ExamQuestion eq : questions) {
            ExamAnswer ans = answerMap.get(eq.getQuestionId());
            QuestionBank qb = questionBankRepository.findById(eq.getQuestionId()).orElse(null);
            BigDecimal weight = eq.getScoreWeight() != null ? eq.getScoreWeight() : BigDecimal.ONE;

            if (qb != null && qb.getQuestionType() == com.vatly1.example.entity.enums.QuestionType.FILL_BLANK) {
                List<QuestionAcceptedAnswer> acceptedAnswers =
                        questionAcceptedAnswerRepository.findByQuestionIdOrderByOrderIndexAsc(eq.getQuestionId());
                List<String> acceptedTexts = acceptedAnswers.stream()
                        .map(QuestionAcceptedAnswer::getAcceptedText)
                        .collect(Collectors.toList());

                String studentText = (ans != null) ? ans.getAnswerText() : null;
                boolean correct = AnswerNormalizationUtil.isMatchAny(studentText, acceptedTexts);

                if (ans == null) {
                    ans = ExamAnswer.builder()
                            .attemptId(attemptId)
                            .questionId(eq.getQuestionId())
                            .build();
                }
                ans.setIsCorrect(correct);
                BigDecimal score = correct ? weight : BigDecimal.ZERO;
                ans.setScore(score);
                if (correct) {
                    totalScore = totalScore.add(score);
                }
                examAnswerRepository.save(ans);
            } else {
                List<QuestionOption> correctOptions = questionOptionRepository.findByQuestionIdAndIsCorrectTrue(eq.getQuestionId());
                Set<UUID> correctOptionIds = correctOptions.stream()
                        .map(QuestionOption::getOptionId)
                        .collect(Collectors.toSet());

                if (ans != null && ans.getSelectedOptionIds() != null && !ans.getSelectedOptionIds().isEmpty()) {
                    Set<UUID> studentSelectedIds = new HashSet<>(ans.getSelectedOptionIds());
                    boolean correct = !correctOptionIds.isEmpty() && studentSelectedIds.equals(correctOptionIds);

                    ans.setIsCorrect(correct);
                    BigDecimal score = correct ? weight : BigDecimal.ZERO;
                    ans.setScore(score);
                    totalScore = totalScore.add(score);
                    examAnswerRepository.save(ans);
                } else if (ans != null) {
                    ans.setIsCorrect(false);
                    ans.setScore(BigDecimal.ZERO);
                    examAnswerRepository.save(ans);
                }
            }
        }
        return totalScore;
    }

    @Override
    @Transactional
    public ExamAttemptDTO submitAttempt(UUID attemptId, UUID studentId) {
        ExamAttempt attempt = examAttemptRepository.findByIdWithLock(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(attempt.getStudentId(), studentId)) {
            throw new CustomException("Access denied: This attempt belongs to another student", HttpStatus.FORBIDDEN);
        }

        if (Boolean.TRUE.equals(attempt.getIsDisqualified()) || attempt.getStatus() == AttemptStatus.DISQUALIFIED) {
            throw new CustomException("Bạn đã bị giảng viên đình chỉ bài thi do vi phạm quy chế. Lý do: " +
                    (attempt.getDisqualifiedReason() != null ? attempt.getDisqualifiedReason() : "Vi phạm quy chế thi"), HttpStatus.FORBIDDEN);
        }

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new CustomException("Attempt is already submitted", HttpStatus.BAD_REQUEST);
        }

        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        if (exam.getEndTime() != null && Instant.now().isAfter(exam.getEndTime())) {
            throw new CustomException("Cannot submit exam: Exam deadline has passed", HttpStatus.BAD_REQUEST);
        }

        BigDecimal totalScore = calculateAndGradeAttemptAnswers(attemptId, attempt.getExamId());

        Instant now = Instant.now();
        Instant expiry = calculateAttemptExpiry(attempt, exam);
        Instant submittedTime = (expiry != null && now.isAfter(expiry)) ? expiry : now;

        attempt.setStatus(AttemptStatus.GRADED);
        attempt.setSubmittedAt(submittedTime);
        attempt.setTotalScore(totalScore);

        ExamAttempt saved = examAttemptRepository.save(attempt);

        try {
            notificationService.sendNotification(
                    saved.getStudentId(),
                    "Kết quả bài thi: " + exam.getTitle(),
                    "Bạn đã hoàn thành bài thi " + exam.getTitle() + ". Điểm số đạt được: " + totalScore + " điểm.",
                    com.vatly1.example.entity.enums.NotificationType.EXAM_GRADED,
                    saved.getAttemptId(),
                    "EXAM_ATTEMPT"
            );
        } catch (Exception e) {
            log.warn("Failed to send exam result notification: {}", e.getMessage());
        }

        return toAttemptDTO(saved);
    }

    @Override
    public ExamAttemptDTO getMyAttempt(UUID examId, UUID studentId) {
        ExamAttempt attempt = examAttemptRepository.findTopByExamIdAndStudentIdOrderByStartedAtDesc(examId, studentId)
                .orElseThrow(() -> new CustomException("No attempt found for this exam", HttpStatus.NOT_FOUND));
        return toAttemptDTO(attempt);
    }

    @Override
    public List<ExamAttemptDTO> getMyAttempts(UUID examId, UUID studentId) {
        List<ExamAttempt> attempts = examAttemptRepository.findByExamIdAndStudentIdOrderByAttemptNumberAsc(examId, studentId);
        return attempts.stream().map(this::toAttemptDTO).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public ExamAttemptDTO getAttemptDetail(UUID attemptId, UUID currentUserId, String role) {
        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));
        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if ("STUDENT".equalsIgnoreCase(role)) {
            if (!Objects.equals(attempt.getStudentId(), currentUserId)) {
                throw new CustomException("Access denied: You cannot view another student's exam attempt", HttpStatus.FORBIDDEN);
            }
        } else if ("INSTRUCTOR".equalsIgnoreCase(role)) {
            Class clazz = classRepository.findById(exam.getClassId())
                    .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));
            boolean isInstructor = Objects.equals(clazz.getInstructorId(), currentUserId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), currentUserId);
            if (!isInstructor && !isStaff) {
                throw new CustomException("Access denied: You are not authorized to view attempts for this class", HttpStatus.FORBIDDEN);
            }
        }

        ExamAttemptDTO dto = toAttemptDTO(attempt);
        List<StudentExamQuestionDTO> questions = buildStudentQuestionsForAttempt(attempt, exam, role);
        dto.setQuestions(questions);
        dto.setExamTitle(exam.getTitle());
        dto.setDurationMinutes(exam.getDurationMinutes());
        dto.setExamStartTime(exam.getStartTime());
        dto.setExamEndTime(exam.getEndTime());
        dto.setTotalQuestions(questions != null ? questions.size() : 0);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentExamQuestionDTO> getAttemptQuestions(UUID attemptId, UUID currentUserId, String role) {
        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));
        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if ("STUDENT".equalsIgnoreCase(role)) {
            if (!Objects.equals(attempt.getStudentId(), currentUserId)) {
                throw new CustomException("Access denied: You cannot view another student's exam attempt", HttpStatus.FORBIDDEN);
            }
            if (Boolean.TRUE.equals(attempt.getIsDisqualified()) || attempt.getStatus() == AttemptStatus.DISQUALIFIED) {
                throw new CustomException("Bạn đã bị giảng viên đình chỉ bài thi do vi phạm quy chế. Lý do: " +
                        (attempt.getDisqualifiedReason() != null ? attempt.getDisqualifiedReason() : "Vi phạm quy chế thi"), HttpStatus.FORBIDDEN);
            }
        } else if ("INSTRUCTOR".equalsIgnoreCase(role)) {
            Class clazz = classRepository.findById(exam.getClassId())
                    .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));
            boolean isInstructor = Objects.equals(clazz.getInstructorId(), currentUserId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), currentUserId);
            if (!isInstructor && !isStaff) {
                throw new CustomException("Access denied: You are not authorized to view attempts for this class", HttpStatus.FORBIDDEN);
            }
        }

        return buildStudentQuestionsForAttempt(attempt, exam, role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamQuestionDetailDTO> getExamQuestions(UUID examId, UUID currentUserId, String role) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if ("INSTRUCTOR".equalsIgnoreCase(role)) {
            checkExamOwnership(exam, currentUserId);
        } else if ("STUDENT".equalsIgnoreCase(role)) {
            if (Boolean.FALSE.equals(exam.getIsPublished())) {
                throw new CustomException("Kỳ thi này hiện chưa được công bố", HttpStatus.FORBIDDEN);
            }
            boolean isEnrolled = classEnrollmentRepository.isStudentActivelyEnrolledInClass(exam.getClassId(), currentUserId);
            boolean isParticipant = examParticipantRepository.existsByExamIdAndStudentId(examId, currentUserId);
            if (!isEnrolled && !isParticipant) {
                throw new CustomException("Access denied: You are not authorized to view questions for this exam", HttpStatus.FORBIDDEN);
            }
            Instant now = Instant.now();
            if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
                throw new CustomException("Exam has not started yet", HttpStatus.BAD_REQUEST);
            }
        }

        boolean hideAnswers = "STUDENT".equalsIgnoreCase(role);
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(examId);
        List<ExamQuestionDetailDTO> results = new ArrayList<>();

        for (ExamQuestion eq : examQuestions) {
            QuestionBank qb = questionBankRepository.findById(eq.getQuestionId()).orElse(null);
            if (qb == null) continue;

            String topicName = null;
            UUID chapterId = null;
            String chapterName = null;
            if (qb.getTopicId() != null) {
                Topic topic = topicRepository.findById(qb.getTopicId()).orElse(null);
                if (topic != null) {
                    topicName = topic.getTopicName();
                    if (topic.getChapterId() != null) {
                        chapterId = topic.getChapterId();
                        Chapter chapter = chapterRepository.findById(topic.getChapterId()).orElse(null);
                        if (chapter != null) {
                            chapterName = chapter.getChapterName();
                        }
                    }
                }
            }

            List<QuestionOptionDTO> optionDTOs;
            List<String> acceptedAnswers = null;

            if (qb.getQuestionType() == com.vatly1.example.entity.enums.QuestionType.FILL_BLANK) {
                optionDTOs = Collections.emptyList();
                if (!hideAnswers) {
                    acceptedAnswers = questionAcceptedAnswerRepository.findByQuestionIdOrderByOrderIndexAsc(qb.getQuestionId())
                            .stream()
                            .map(QuestionAcceptedAnswer::getAcceptedText)
                            .collect(Collectors.toList());
                }
            } else {
                List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(qb.getQuestionId());
                optionDTOs = options.stream()
                        .map(opt -> QuestionOptionDTO.builder()
                                .optionId(opt.getOptionId())
                                .questionId(opt.getQuestionId())
                                .content(opt.getOptionText())
                                .isCorrect(hideAnswers ? null : opt.getIsCorrect())
                                .orderIndex(opt.getOrderIndex())
                                .build())
                        .collect(Collectors.toList());
            }

            results.add(ExamQuestionDetailDTO.builder()
                    .examId(examId)
                    .questionId(qb.getQuestionId())
                    .content(qb.getContent())
                    .mediaUrl(qb.getMediaUrl())
                    .questionType(qb.getQuestionType())
                    .difficultyLevel(qb.getDifficultyLevel())
                    .topicId(qb.getTopicId())
                    .topicName(topicName)
                    .chapterId(chapterId)
                    .chapterName(chapterName)
                    .orderIndex(eq.getOrderIndex())
                    .scoreWeight(eq.getScoreWeight())
                    .options(optionDTOs)
                    .acceptedAnswers(acceptedAnswers)
                    .build());
        }

        return results;
    }

    private boolean hasActiveOrUpcomingFinalExamsSharingQuestions(Exam currentExam, List<ExamQuestion> examQuestions) {
        if (currentExam.getExamType() != com.vatly1.example.entity.enums.ExamType.FINAL) {
            return false;
        }
        Instant now = Instant.now();
        // 1. Kiểm tra các đề FINAL khác cùng matrixId (nếu đề được sinh từ ma trận chung)
        if (currentExam.getMatrixId() != null) {
            List<Exam> sameMatrixExams = examRepository.findByMatrixId(currentExam.getMatrixId());
            for (Exam other : sameMatrixExams) {
                if (!Objects.equals(other.getExamId(), currentExam.getExamId())
                        && other.getExamType() == com.vatly1.example.entity.enums.ExamType.FINAL) {
                    if (other.getEndTime() == null || !now.isAfter(other.getEndTime())) {
                        return true;
                    }
                }
            }
        }
        // 2. Kiểm tra các đề FINAL khác chia sẻ bất kỳ câu hỏi nào trong đề này
        if (examQuestions != null && !examQuestions.isEmpty()) {
            List<UUID> questionIds = examQuestions.stream()
                    .map(ExamQuestion::getQuestionId)
                    .collect(Collectors.toList());
            List<UUID> relatedExamIds = examQuestionRepository.findDistinctExamIdsByQuestionIdIn(questionIds);
            for (UUID otherId : relatedExamIds) {
                if (!Objects.equals(otherId, currentExam.getExamId())) {
                    Exam other = examRepository.findById(otherId).orElse(null);
                    if (other != null && other.getExamType() == com.vatly1.example.entity.enums.ExamType.FINAL) {
                        if (other.getEndTime() == null || !now.isAfter(other.getEndTime())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<StudentExamQuestionDTO> buildStudentQuestionsForAttempt(ExamAttempt attempt, Exam exam, String role) {
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(attempt.getExamId());
        boolean isGraded = attempt.getStatus() == AttemptStatus.GRADED;
        boolean isStaffOrAdmin = "ADMIN".equalsIgnoreCase(role) || "INSTRUCTOR".equalsIgnoreCase(role) || "TA".equalsIgnoreCase(role);
        boolean isExamEnded = exam.getEndTime() != null && Instant.now().isAfter(exam.getEndTime());
        boolean hasConflictFinal = hasActiveOrUpcomingFinalExamsSharingQuestions(exam, examQuestions);
        boolean answersAllowed = isStaffOrAdmin || (isGraded && (exam.getExamType() != com.vatly1.example.entity.enums.ExamType.FINAL || (isExamEnded && !hasConflictFinal)));
        if (!isStaffOrAdmin && exam.getExamType() == com.vatly1.example.entity.enums.ExamType.PRACTICE && classSettingsService != null && exam.getClassId() != null) {
            com.vatly1.example.entity.ClassSettings settings = classSettingsService.getEffectiveSettings(exam.getClassId());
            if (!Boolean.TRUE.equals(settings.getPracticeShowSolution())) {
                answersAllowed = false;
            }
        }
        final boolean canSeeAnswers = answersAllowed;
        List<ExamAnswer> studentAnswers = examAnswerRepository.findByAttemptId(attempt.getAttemptId());
        Map<UUID, ExamAnswer> answerMap = studentAnswers.stream()
                .collect(Collectors.toMap(ExamAnswer::getQuestionId, a -> a, (a1, a2) -> a1));

        List<StudentExamQuestionDTO> results = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) {
            QuestionBank qb = questionBankRepository.findById(eq.getQuestionId()).orElse(null);
            if (qb == null) continue;

            String topicName = null;
            UUID chapterId = null;
            String chapterName = null;
            if (qb.getTopicId() != null) {
                Topic topic = topicRepository.findById(qb.getTopicId()).orElse(null);
                if (topic != null) {
                    topicName = topic.getTopicName();
                    if (topic.getChapterId() != null) {
                        chapterId = topic.getChapterId();
                        Chapter chapter = chapterRepository.findById(topic.getChapterId()).orElse(null);
                        if (chapter != null) {
                            chapterName = chapter.getChapterName();
                        }
                    }
                }
            }

            List<StudentQuestionOptionDTO> optionDTOs;
            List<String> acceptedAnswers = null;

            if (qb.getQuestionType() == com.vatly1.example.entity.enums.QuestionType.FILL_BLANK) {
                optionDTOs = Collections.emptyList();
                if (canSeeAnswers) {
                    acceptedAnswers = questionAcceptedAnswerRepository.findByQuestionIdOrderByOrderIndexAsc(qb.getQuestionId())
                            .stream()
                            .map(QuestionAcceptedAnswer::getAcceptedText)
                            .collect(Collectors.toList());
                }
            } else {
                List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(qb.getQuestionId());
                optionDTOs = options.stream()
                        .map(opt -> StudentQuestionOptionDTO.builder()
                                .optionId(opt.getOptionId())
                                .questionId(opt.getQuestionId())
                                .content(opt.getOptionText())
                                .orderIndex(opt.getOrderIndex())
                                .isCorrect(canSeeAnswers ? opt.getIsCorrect() : null)
                                .build())
                        .collect(Collectors.toList());
            }

            ExamAnswer ans = answerMap.get(eq.getQuestionId());

            results.add(StudentExamQuestionDTO.builder()
                    .examId(exam.getExamId())
                    .questionId(qb.getQuestionId())
                    .content(qb.getContent())
                    .mediaUrl(qb.getMediaUrl())
                    .questionType(qb.getQuestionType())
                    .difficultyLevel(qb.getDifficultyLevel())
                    .topicId(qb.getTopicId())
                    .topicName(topicName)
                    .chapterId(chapterId)
                    .chapterName(chapterName)
                    .orderIndex(eq.getOrderIndex())
                    .scoreWeight(eq.getScoreWeight())
                    .options(optionDTOs)
                    .acceptedAnswers(acceptedAnswers)
                    .selectedOptionIds(ans != null && ans.getSelectedOptionIds() != null ? ans.getSelectedOptionIds() : new ArrayList<>())
                    .answerText(ans != null ? ans.getAnswerText() : null)
                    .isCorrect(canSeeAnswers && ans != null ? ans.getIsCorrect() : null)
                    .score(canSeeAnswers && ans != null ? ans.getScore() : null)
                    .build());
        }

        return results;
    }

    @Override
    @Transactional
    public void removeQuestionFromExam(UUID examId, UUID questionId, UUID instructorId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        checkExamOwnership(exam, instructorId);

        if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.FINAL) {
            User user = userRepository.findById(instructorId).orElse(null);
            boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
            if (!isAdmin) {
                throw new CustomException("Chỉ Quản trị viên (ADMIN) mới có quyền điều chỉnh câu hỏi kỳ thi cuối kỳ (FINAL)", HttpStatus.FORBIDDEN);
            }
        }

        if (examAttemptRepository.countByExamId(examId) > 0) {
            throw new CustomException("Cannot remove question from an exam that already has student attempts", HttpStatus.BAD_REQUEST);
        }

        if (exam.getEndTime() != null && Instant.now().isAfter(exam.getEndTime())) {
            throw new CustomException("Cannot modify questions for an exam that has already ended", HttpStatus.BAD_REQUEST);
        }

        if (!examQuestionRepository.existsByExamIdAndQuestionId(examId, questionId)) {
            throw new CustomException("Question not found in this exam", HttpStatus.NOT_FOUND);
        }

        examQuestionRepository.deleteByExamIdAndQuestionId(examId, questionId);
    }

    @Override
    @Transactional
    public ExamDTO updateExam(UUID examId, UpdateExamDTO dto, UUID instructorId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        Class clazz = checkExamOwnership(exam, instructorId);
        User currentUser = resolveCurrentUser(instructorId);

        boolean hasAttempts = examAttemptRepository.existsByExamId(examId);
        if (hasAttempts) {
            if (dto.getExamType() != null && dto.getExamType() != exam.getExamType()) {
                throw new CustomException("Không thể thay đổi hình thức thi (examType) của kỳ thi đã có thí sinh làm bài", HttpStatus.BAD_REQUEST);
            }
            if (dto.getMatrixId() != null && !Objects.equals(dto.getMatrixId(), exam.getMatrixId())) {
                throw new CustomException("Không thể thay đổi ma trận đề thi (matrixId) của kỳ thi đã có thí sinh làm bài", HttpStatus.BAD_REQUEST);
            }
        }

        if (dto.getExamType() != null) {
            validateExamManagePermission(clazz, currentUser, dto.getExamType());
        }

        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            exam.setTitle(dto.getTitle().trim());
        }
        if (!hasAttempts && dto.getExamType() != null) {
            exam.setExamType(dto.getExamType());
        }
        if (dto.getDurationMinutes() != null) {
            if (dto.getDurationMinutes() <= 0) {
                throw new CustomException("Thời lượng làm bài phải lớn hơn 0", HttpStatus.BAD_REQUEST);
            }
            exam.setDurationMinutes(dto.getDurationMinutes());
        }
        if (dto.getStartTime() != null) {
            exam.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            exam.setEndTime(dto.getEndTime());
        }
        if (exam.getStartTime() != null && exam.getEndTime() != null && exam.getStartTime().isAfter(exam.getEndTime())) {
            throw new CustomException("Thời gian bắt đầu không được sau thời gian kết thúc", HttpStatus.BAD_REQUEST);
        }
        if (!hasAttempts && dto.getMatrixId() != null) {
            ExamMatrix matrix = examMatrixRepository.findById(dto.getMatrixId())
                    .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi", HttpStatus.NOT_FOUND));
            if (!Objects.equals(matrix.getSubjectId(), clazz.getSubjectId())) {
                throw new CustomException("Ma trận đề thi không thuộc môn học của lớp học này", HttpStatus.BAD_REQUEST);
            }
            if (currentUser.getRole() != UserRole.ADMIN && matrix.getExamType() == com.vatly1.example.entity.enums.ExamType.FINAL) {
                throw new CustomException("Giảng viên không được phép sử dụng ma trận đề thi cuối kỳ (FINAL)", HttpStatus.FORBIDDEN);
            }
            exam.setMatrixId(dto.getMatrixId());
        }

        if (dto.getProctoringMode() != null) {
            exam.setProctoringMode(dto.getProctoringMode());
        }
        if (dto.getMaxViolationsAllowed() != null) {
            exam.setMaxViolationsAllowed(dto.getMaxViolationsAllowed());
        }
        if (dto.getAutoBanOnLimit() != null) {
            exam.setAutoBanOnLimit(dto.getAutoBanOnLimit());
        }
        if (dto.getIsPublished() != null) {
            boolean wasUnpublished = Boolean.FALSE.equals(exam.getIsPublished());
            exam.setIsPublished(dto.getIsPublished());
            if (wasUnpublished && Boolean.TRUE.equals(dto.getIsPublished())) {
                try {
                    notificationService.sendNotificationToClass(
                            exam.getClassId(),
                            "Bài thi mới: " + exam.getTitle(),
                            "Lớp học vừa công bố bài thi: " + exam.getTitle() + " (Thời lượng: " + exam.getDurationMinutes() + " phút). Vui lòng hoàn thành đúng thời hạn.",
                            com.vatly1.example.entity.enums.NotificationType.EXAM_NEW,
                            exam.getExamId(),
                            "EXAM"
                    );
                } catch (Exception e) {
                    log.warn("Failed to send exam publication notification: {}", e.getMessage());
                }
            }
        }

        Exam updated = examRepository.save(exam);
        return toExamDTO(updated);
    }

    @Override
    @Transactional
    public void deleteExam(UUID examId, UUID instructorId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        checkExamOwnership(exam, instructorId);

        User user = userRepository.findById(instructorId).orElse(null);
        boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
        if (!isAdmin && exam.getExamType() == com.vatly1.example.entity.enums.ExamType.FINAL) {
            throw new CustomException("Chỉ Quản trị viên (ADMIN) mới có quyền xóa kỳ thi cuối kỳ (FINAL)", HttpStatus.FORBIDDEN);
        }

        if (examAttemptRepository.existsByExamId(examId)) {
            throw new CustomException("Không thể xóa kỳ thi đã có sinh viên làm bài", HttpStatus.BAD_REQUEST);
        }

        examQuestionRepository.deleteByExamId(examId);
        examParticipantRepository.deleteByExamId(examId);
        examViolationLogRepository.deleteByExamId(examId);
        examRepository.delete(exam);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamAttemptSummaryDTO> getExamAttempts(UUID examId, UUID currentUserId, String role) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equalsIgnoreCase(role)) {
            checkExamOwnership(exam, currentUserId);
        }

        List<ExamAttempt> attempts = examAttemptRepository.findByExamIdOrderByStartedAtDesc(examId);
        int totalQuestions = (int) examQuestionRepository.countByExamId(examId);

        List<ExamAttemptSummaryDTO> results = new ArrayList<>();
        for (ExamAttempt att : attempts) {
            User student = userRepository.findById(att.getStudentId()).orElse(null);
            UserProfile profile = userProfileRepository.findById(att.getStudentId()).orElse(null);

            String username = student != null ? student.getUsername() : null;
            String studentName = (profile != null && profile.getFullName() != null) ? profile.getFullName() : username;
            String studentCode = profile != null ? profile.getStudentCode() : null;

            List<ExamAnswer> answers = examAnswerRepository.findByAttemptId(att.getAttemptId());
            int correctCount = (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();

            results.add(ExamAttemptSummaryDTO.builder()
                    .attemptId(att.getAttemptId())
                    .examId(att.getExamId())
                    .examTitle(exam.getTitle())
                    .studentId(att.getStudentId())
                    .studentUsername(username)
                    .studentName(studentName)
                    .studentCode(studentCode)
                    .attemptNumber(att.getAttemptNumber())
                    .status(att.getStatus())
                    .startedAt(att.getStartedAt())
                    .submittedAt(att.getSubmittedAt())
                    .totalScore(att.getTotalScore())
                    .totalQuestions(totalQuestions)
                    .correctAnswersCount(correctCount)
                    .violationCount(att.getViolationCount() != null ? att.getViolationCount() : 0)
                    .isDisqualified(Boolean.TRUE.equals(att.getIsDisqualified()))
                    .disqualifiedReason(att.getDisqualifiedReason())
                    .disqualifiedAt(att.getDisqualifiedAt())
                    .build());
        }

        return results;
    }

    @Override
    @Transactional
    public ExamAttemptDTO gradeAttempt(UUID attemptId, GradeAttemptDTO dto, UUID instructorId) {
        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));
        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        User user = userRepository.findById(instructorId).orElse(null);
        boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
        if (!isAdmin) {
            checkExamOwnership(exam, instructorId);
        }

        attempt.setTotalScore(dto.getTotalScore());
        attempt.setStatus(AttemptStatus.GRADED);
        if (attempt.getSubmittedAt() == null) {
            attempt.setSubmittedAt(Instant.now());
        }
        ExamAttempt saved = examAttemptRepository.save(attempt);

        try {
            String feedbackMsg = (dto.getFeedback() != null && !dto.getFeedback().isBlank())
                    ? ". Nhận xét: " + dto.getFeedback().trim()
                    : "";
            notificationService.sendNotification(
                    saved.getStudentId(),
                    "Điểm thi đã được cập nhật: " + exam.getTitle(),
                    "Giảng viên đã chấm/điều chỉnh điểm bài thi " + exam.getTitle() + ". Điểm số: " + dto.getTotalScore() + feedbackMsg,
                    com.vatly1.example.entity.enums.NotificationType.EXAM_GRADED,
                    saved.getAttemptId(),
                    "EXAM_ATTEMPT"
            );
        } catch (Exception e) {
            log.warn("Failed to send grade notification: {}", e.getMessage());
        }

        return toAttemptDTO(saved);
    }

    private Class checkExamOwnership(Exam exam, UUID instructorId) {
        Class clazz = classRepository.findById(exam.getClassId())
                .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));
        User currentUser = resolveCurrentUser(instructorId);
        validateExamManagePermission(clazz, currentUser, exam.getExamType());
        return clazz;
    }

    private void validateExamManagePermission(Class clazz, User user, com.vatly1.example.entity.enums.ExamType examType) {
        if (user == null) {
            throw new CustomException("Vui lòng đăng nhập để thực hiện thao tác này", HttpStatus.UNAUTHORIZED);
        }
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        if (user.getRole() != UserRole.INSTRUCTOR) {
            throw new CustomException("Chỉ Giảng viên (INSTRUCTOR) hoặc Quản trị viên (ADMIN) mới có quyền tạo hoặc chỉnh sửa bài thi", HttpStatus.FORBIDDEN);
        }

        boolean isPrimary = Objects.equals(clazz.getInstructorId(), user.getUserId());
        boolean isStaffInstructor = classStaffRepository.existsByClassIdAndUserIdAndRoleInClass(
                clazz.getClassId(), user.getUserId(), ClassStaffRole.INSTRUCTOR);
        if (!isPrimary && !isStaffInstructor) {
            throw new CustomException("Giảng viên không được phân công giảng dạy lớp học này hoặc không có quyền quản lý bài thi", HttpStatus.FORBIDDEN);
        }

        if (examType == com.vatly1.example.entity.enums.ExamType.FINAL) {
            throw new CustomException("Chỉ Quản trị viên (ADMIN) mới có quyền tạo hoặc cấu hình kỳ thi cuối kỳ (FINAL)", HttpStatus.FORBIDDEN);
        }
    }

    private User resolveCurrentUser(UUID fallbackUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            User user = userRepository.findByUsername(auth.getName());
            if (user != null) {
                return user;
            }
        }
        if (fallbackUserId != null) {
            return userRepository.findById(fallbackUserId).orElse(null);
        }
        return null;
    }

    private ExamDTO toExamDTO(Exam exam) {
        int totalQuestions = (int) examQuestionRepository.countByExamId(exam.getExamId());
        return ExamDTO.builder()
                .examId(exam.getExamId())
                .classId(exam.getClassId())
                .matrixId(exam.getMatrixId())
                .title(exam.getTitle())
                .examType(exam.getExamType())
                .durationMinutes(exam.getDurationMinutes())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .createdBy(exam.getCreatedBy())
                .createdAt(exam.getCreatedAt())
                .totalQuestions(totalQuestions)
                .proctoringMode(exam.getProctoringMode())
                .maxViolationsAllowed(exam.getMaxViolationsAllowed())
                .autoBanOnLimit(exam.getAutoBanOnLimit())
                .isPublished(exam.getIsPublished() != null ? exam.getIsPublished() : true)
                .build();
    }

    private ExamAttemptDTO toAttemptDTO(ExamAttempt attempt) {
        Exam exam = examRepository.findById(attempt.getExamId()).orElse(null);
        return ExamAttemptDTO.builder()
                .attemptId(attempt.getAttemptId())
                .examId(attempt.getExamId())
                .studentId(attempt.getStudentId())
                .attemptNumber(attempt.getAttemptNumber())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .status(attempt.getStatus())
                .totalScore(attempt.getTotalScore())
                .violationCount(attempt.getViolationCount() != null ? attempt.getViolationCount() : 0)
                .isDisqualified(Boolean.TRUE.equals(attempt.getIsDisqualified()))
                .disqualifiedReason(attempt.getDisqualifiedReason())
                .disqualifiedAt(attempt.getDisqualifiedAt())
                .disqualifiedBy(attempt.getDisqualifiedBy())
                .proctoringMode(exam != null ? exam.getProctoringMode() : null)
                .maxViolationsAllowed(exam != null ? exam.getMaxViolationsAllowed() : null)
                .autoBanOnLimit(exam != null ? exam.getAutoBanOnLimit() : null)
                .examTitle(exam != null ? exam.getTitle() : null)
                .durationMinutes(exam != null ? exam.getDurationMinutes() : null)
                .examStartTime(exam != null ? exam.getStartTime() : null)
                .examEndTime(exam != null ? exam.getEndTime() : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ExamAttemptProgressDTO getAttemptProgress(UUID attemptId, UUID currentUserId, String role) {
        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));
        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if ("STUDENT".equalsIgnoreCase(role)) {
            if (!Objects.equals(attempt.getStudentId(), currentUserId)) {
                throw new CustomException("Access denied: You cannot view another student's exam attempt", HttpStatus.FORBIDDEN);
            }
        }

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(attempt.getExamId());
        int totalQuestions = examQuestions != null ? examQuestions.size() : 0;

        List<ExamAnswer> answers = examAnswerRepository.findByAttemptId(attemptId);
        int answeredQuestions = 0;
        if (answers != null) {
            for (ExamAnswer ans : answers) {
                boolean hasSelected = ans.getSelectedOptionIds() != null && !ans.getSelectedOptionIds().isEmpty();
                boolean hasText = ans.getAnswerText() != null && !ans.getAnswerText().trim().isEmpty();
                if (hasSelected || hasText) {
                    answeredQuestions++;
                }
            }
        }

        Instant startedAt = attempt.getStartedAt();
        Integer durationMinutes = exam.getDurationMinutes();
        Instant expiresAt = null;
        long remainingSeconds = 0;
        boolean isExpired = false;

        if (startedAt != null && durationMinutes != null) {
            Instant calculatedExpiry = startedAt.plus(durationMinutes, java.time.temporal.ChronoUnit.MINUTES);
            if (exam.getEndTime() != null && exam.getEndTime().isBefore(calculatedExpiry)) {
                expiresAt = exam.getEndTime();
            } else {
                expiresAt = calculatedExpiry;
            }

            Instant now = Instant.now();
            if (now.isAfter(expiresAt)) {
                remainingSeconds = 0;
                isExpired = true;
            } else {
                remainingSeconds = java.time.Duration.between(now, expiresAt).getSeconds();
            }
        }

        return ExamAttemptProgressDTO.builder()
                .attemptId(attempt.getAttemptId())
                .examId(exam.getExamId())
                .examTitle(exam.getTitle())
                .status(attempt.getStatus())
                .totalQuestions(totalQuestions)
                .answeredQuestions(answeredQuestions)
                .startedAt(startedAt)
                .submittedAt(attempt.getSubmittedAt())
                .durationMinutes(durationMinutes)
                .expiresAt(expiresAt)
                .remainingSeconds(remainingSeconds)
                .isExpired(isExpired)
                .violationCount(attempt.getViolationCount() != null ? attempt.getViolationCount() : 0)
                .isDisqualified(Boolean.TRUE.equals(attempt.getIsDisqualified()))
                .disqualifiedReason(attempt.getDisqualifiedReason())
                .proctoringMode(exam.getProctoringMode())
                .maxViolationsAllowed(exam.getMaxViolationsAllowed())
                .build();
    }

    @Override
    @Transactional
    public BatchSaveResultDTO autosaveAnswers(UUID attemptId, BatchSubmitAnswerDTO dto, UUID studentId) {
        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(attempt.getStudentId(), studentId)) {
            throw new CustomException("Access denied: This attempt belongs to another student", HttpStatus.FORBIDDEN);
        }

        if (Boolean.TRUE.equals(attempt.getIsDisqualified()) || attempt.getStatus() == AttemptStatus.DISQUALIFIED) {
            throw new CustomException("Bạn đã bị giảng viên đình chỉ bài thi do vi phạm quy chế. Lý do: " +
                    (attempt.getDisqualifiedReason() != null ? attempt.getDisqualifiedReason() : "Vi phạm quy chế thi"), HttpStatus.FORBIDDEN);
        }

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new CustomException("Cannot autosave answer: Attempt is already completed", HttpStatus.BAD_REQUEST);
        }

        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        if (isAttemptExpired(attempt, exam)) {
            throw new CustomException("Cannot autosave answer: Attempt time has expired", HttpStatus.BAD_REQUEST);
        }

        int count = 0;
        if (dto != null && dto.getAnswers() != null) {
            for (SubmitAnswerDTO answerDTO : dto.getAnswers()) {
                if (answerDTO.getQuestionId() == null) continue;
                submitAnswer(attemptId, answerDTO, studentId);
                count++;
            }
        }

        return BatchSaveResultDTO.builder()
                .attemptId(attemptId)
                .savedCount(count)
                .savedAt(Instant.now())
                .message("Autosaved " + count + " answers successfully")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ExamAttemptPolicyDTO getExamAttemptPolicy(UUID examId, UUID studentId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        User studentUser = userRepository.findById(studentId).orElse(null);
        boolean isAdmin = studentUser != null && studentUser.getRole() == UserRole.ADMIN;
        if (!isAdmin) {
            if (Boolean.FALSE.equals(exam.getIsPublished())) {
                throw new CustomException("Kỳ thi này hiện chưa được công bố", HttpStatus.FORBIDDEN);
            }
            boolean isEnrolled = classEnrollmentRepository.isStudentActivelyEnrolledInClass(exam.getClassId(), studentId);
            boolean isTransferred = examParticipantRepository.existsByExamIdAndStudentId(examId, studentId);
            if (!isEnrolled && !isTransferred) {
                throw new CustomException("Bạn không có tên trong danh sách ca thi này", HttpStatus.FORBIDDEN);
            }
        }

        Instant now = Instant.now();
        boolean isStarted = exam.getStartTime() == null || !now.isBefore(exam.getStartTime());
        boolean isEnded = exam.getEndTime() != null && now.isAfter(exam.getEndTime());

        int maxAttempts = 1;
        boolean activityAllowed = true;
        if (classSettingsService != null && exam.getClassId() != null) {
            com.vatly1.example.entity.ClassSettings settings = classSettingsService.getEffectiveSettings(exam.getClassId());
            if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.PRACTICE) {
                activityAllowed = Boolean.TRUE.equals(settings.getPracticeEnabled());
                maxAttempts = settings.getMaxPracticeAttempts() != null ? settings.getMaxPracticeAttempts() : 10;
            } else if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.QUIZ) {
                activityAllowed = Boolean.TRUE.equals(settings.getQuizEnabled());
            }
        } else if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.PRACTICE) {
            maxAttempts = 10;
            if (systemSettingService != null) {
                try {
                    com.vatly1.example.entity.SystemSetting maxSetting = systemSettingService.getSettingByKey("exam.max_attempts");
                    if (maxSetting != null && maxSetting.getSettingValue() != null) {
                        maxAttempts = Integer.parseInt(maxSetting.getSettingValue().trim());
                    }
                } catch (Exception ignored) {}
            }
        }

        long usedCount = examAttemptRepository.countByExamIdAndStudentId(examId, studentId);
        int usedAttempts = (int) usedCount;
        int remainingAttempts = Math.max(0, maxAttempts - usedAttempts);

        java.util.Optional<ExamAttempt> inProgress = examAttemptRepository.findFirstByExamIdAndStudentIdAndStatus(
                examId, studentId, AttemptStatus.IN_PROGRESS);
        boolean hasInProgress = inProgress.isPresent();
        UUID currentAttemptId = inProgress.map(ExamAttempt::getAttemptId).orElse(null);

        long questionCount = examQuestionRepository.countByExamId(examId);
        boolean hasQuestions = questionCount > 0;
        boolean canStart = isStarted && !isEnded && !hasInProgress && remainingAttempts > 0 && hasQuestions && activityAllowed;

        return ExamAttemptPolicyDTO.builder()
                .examId(exam.getExamId())
                .examTitle(exam.getTitle())
                .examType(exam.getExamType())
                .maxAttempts(maxAttempts)
                .usedAttempts(usedAttempts)
                .remainingAttempts(remainingAttempts)
                .canStartAttempt(canStart)
                .hasInProgressAttempt(hasInProgress)
                .currentAttemptId(currentAttemptId)
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .isStarted(isStarted)
                .isEnded(isEnded)
                .durationMinutes(exam.getDurationMinutes())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BulkProvisionFinalExamResultDTO bulkProvisionFinalExams(BulkProvisionFinalExamDTO dto, UUID adminId) {
        User admin = userRepository.findById(adminId).orElse(null);
        if (admin == null || admin.getRole() != UserRole.ADMIN) {
            throw new CustomException("Chỉ Quản trị viên (ADMIN) mới có quyền khởi tạo kỳ thi cuối kỳ hàng loạt (Bulk Provisioning)", HttpStatus.FORBIDDEN);
        }

        if (dto.getMatrixId() == null) {
            throw new CustomException("matrixId không được để trống", HttpStatus.BAD_REQUEST);
        }

        ExamMatrix matrix = examMatrixRepository.findByIdForUpdate(dto.getMatrixId())
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi với ID: " + dto.getMatrixId(), HttpStatus.NOT_FOUND));

        if (matrix.getExamType() != com.vatly1.example.entity.enums.ExamType.FINAL) {
            throw new CustomException("Chỉ ma trận đề thi loại FINAL mới được sử dụng cho Bulk Provisioning FINAL", HttpStatus.BAD_REQUEST);
        }

        if (matrix.getStatus() != MatrixStatus.PUBLISHED || !Boolean.TRUE.equals(matrix.getIsLocked())) {
            throw new CustomException(String.format("Ma trận đề thi phải ở trạng thái PUBLISHED và đã khóa (isLocked=true) mới được dùng để tạo kỳ thi. Trạng thái hiện tại: %s, isLocked: %s",
                    matrix.getStatus(), matrix.getIsLocked()), HttpStatus.BAD_REQUEST);
        }

        if (dto.getClassIds() == null || dto.getClassIds().isEmpty()) {
            throw new CustomException("Danh sách mã lớp (classIds) không được để trống", HttpStatus.BAD_REQUEST);
        }

        // Chống duplicate UUID trong classIds
        Set<UUID> uniqueClassIds = new HashSet<>(dto.getClassIds());
        if (uniqueClassIds.size() != dto.getClassIds().size()) {
            throw new CustomException("Danh sách lớp học (classIds) chứa ID trùng lặp", HttpStatus.BAD_REQUEST);
        }

        if (dto.getDurationMinutes() == null || dto.getDurationMinutes() <= 0) {
            throw new CustomException("Thời lượng thi (durationMinutes) phải lớn hơn 0", HttpStatus.BAD_REQUEST);
        }

        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new CustomException("Thời gian bắt đầu và kết thúc kỳ thi không được để trống", HttpStatus.BAD_REQUEST);
        }

        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new CustomException("Thời gian bắt đầu thi phải trước thời gian kết thúc thi", HttpStatus.BAD_REQUEST);
        }

        // Revalidate tập câu hỏi APPROVED nếu autoGenerateQuestions = true
        if (Boolean.TRUE.equals(dto.getAutoGenerateQuestions())) {
            List<ExamMatrixDetail> details = examMatrixDetailRepository.findByMatrixId(matrix.getMatrixId());
            if (details == null || details.isEmpty()) {
                throw new CustomException("Ma trận đề thi không có cấu hình chi tiết (details)", HttpStatus.BAD_REQUEST);
            }
            Set<UUID> topicIds = details.stream().map(ExamMatrixDetail::getTopicId).filter(Objects::nonNull).collect(Collectors.toSet());
            List<QuestionBank> candidates = topicIds.isEmpty() ? Collections.emptyList() :
                    questionBankRepository.findBySubjectIdAndTopicIdInAndApprovalStatus(matrix.getSubjectId(), topicIds, ApprovalStatus.APPROVED)
                            .stream()
                            .filter(q -> q.getQuestionType() == QuestionType.MCQ_SINGLE || q.getQuestionType() == QuestionType.FILL_BLANK)
                            .collect(Collectors.toList());
            Set<UUID> candidateIds = candidates.stream().map(QuestionBank::getQuestionId).collect(Collectors.toSet());
            List<QuestionClo> questionClos = candidateIds.isEmpty() ? Collections.emptyList() :
                    questionCloRepository.findBySubjectIdAndQuestionIdIn(matrix.getSubjectId(), candidateIds);
            Map<UUID, Set<UUID>> questionCloMap = new HashMap<>();
            for (QuestionClo qc : questionClos) {
                questionCloMap.computeIfAbsent(qc.getQuestionId(), k -> new HashSet<>()).add(qc.getCloId());
            }
            com.vatly1.example.service.algorithm.CloAwareQuestionMatcher.MatchResult matchResult =
                    cloAwareQuestionMatcher.match(details, candidates, questionCloMap, null);
            if (!matchResult.isFeasible()) {
                String warnMsg = matchResult.getDiagnostics().isEmpty()
                        ? String.format("Cần %d câu nhưng chỉ phân bổ được %d câu", matchResult.getTotalRequiredQuestions(), matchResult.getTotalMatchedQuestions())
                        : String.join("; ", matchResult.getDiagnostics());
                throw new CustomException("Không thể khởi tạo kỳ thi hàng loạt: Ngân hàng câu hỏi không đáp ứng ma trận đề: " + warnMsg, HttpStatus.BAD_REQUEST);
            }
        }

        // Pre-lock all classes in deterministic sorted UUID order to prevent deadlocks
        List<UUID> sortedClassIds = dto.getClassIds().stream().distinct().sorted().collect(Collectors.toList());
        Map<UUID, Class> classMap = new HashMap<>();
        for (UUID classId : sortedClassIds) {
            Class clazz = classRepository.findByIdForUpdate(classId)
                    .orElseThrow(() -> new CustomException("Không tìm thấy lớp học với ID: " + classId, HttpStatus.NOT_FOUND));
            classMap.put(classId, clazz);
        }

        List<ExamDTO> createdExamDTOs = new ArrayList<>();

        for (UUID classId : dto.getClassIds()) {
            Class clazz = classMap.get(classId);

            if (!Objects.equals(clazz.getSubjectId(), matrix.getSubjectId())) {
                throw new CustomException(String.format("Lớp học '%s' (ID: %s) không thuộc môn học của ma trận đề thi",
                        clazz.getClassCode() != null ? clazz.getClassCode() : classId, classId), HttpStatus.BAD_REQUEST);
            }

            if (dto.getSemesterId() != null && !Objects.equals(clazz.getSemesterId(), dto.getSemesterId())) {
                throw new CustomException(String.format("Lớp học '%s' không thuộc học kỳ được chỉ định (ID: %s)",
                        clazz.getClassCode() != null ? clazz.getClassCode() : classId, dto.getSemesterId()), HttpStatus.BAD_REQUEST);
            }

            if (examRepository.existsByClassIdAndMatrixId(classId, matrix.getMatrixId())) {
                throw new CustomException(String.format("Lớp học '%s' đã có bài thi FINAL sử dụng ma trận đề này (Revision %d)",
                        clazz.getClassCode() != null ? clazz.getClassCode() : classId, matrix.getRevisionNumber()), HttpStatus.CONFLICT);
            }

            String examTitle = dto.getTitle() != null && !dto.getTitle().isBlank()
                    ? dto.getTitle().trim()
                    : String.format("%s - %s", matrix.getMatrixName(), clazz.getClassCode() != null ? clazz.getClassCode() : "Class");

            Exam exam = Exam.builder()
                    .classId(classId)
                    .matrixId(matrix.getMatrixId())
                    .title(examTitle)
                    .examType(com.vatly1.example.entity.enums.ExamType.FINAL)
                    .durationMinutes(dto.getDurationMinutes())
                    .startTime(dto.getStartTime())
                    .endTime(dto.getEndTime())
                    .createdBy(adminId)
                    .createdAt(Instant.now())
                    .build();

            Exam savedExam = examRepository.save(exam);

            if (Boolean.TRUE.equals(dto.getAutoGenerateQuestions())) {
                populateFinalExamQuestions(savedExam, matrix);
            }

            try {
                notificationService.sendNotificationToClass(
                        savedExam.getClassId(),
                        "Kỳ thi cuối kỳ: " + savedExam.getTitle(),
                        "Lớp học đã được xếp lịch kỳ thi cuối kỳ: " + savedExam.getTitle() + " (Thời lượng: " + savedExam.getDurationMinutes() + " phút).",
                        com.vatly1.example.entity.enums.NotificationType.EXAM_NEW,
                        savedExam.getExamId(),
                        "EXAM"
                );
            } catch (Exception e) {
                log.warn("Failed to send bulk provision notification: {}", e.getMessage());
            }

            createdExamDTOs.add(toExamDTO(savedExam));
        }

        log.info("Bulk provisioned FINAL exams successfully: {} classes for matrix {} (Rev {}) by admin {}",
                createdExamDTOs.size(), matrix.getMatrixId(), matrix.getRevisionNumber(), adminId);

        return BulkProvisionFinalExamResultDTO.builder()
                .matrixId(matrix.getMatrixId())
                .matrixName(matrix.getMatrixName())
                .revisionNumber(matrix.getRevisionNumber())
                .totalClassesRequested(dto.getClassIds().size())
                .totalClassesProvisioned(createdExamDTOs.size())
                .createdExams(createdExamDTOs)
                .build();
    }

    @Override
    @Transactional
    public ViolationResultDTO logViolation(UUID attemptId, LogViolationRequestDTO dto, UUID studentId) {
        ExamAttempt attempt = examAttemptRepository.findByIdWithLock(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(attempt.getStudentId(), studentId)) {
            throw new CustomException("Access denied: This attempt belongs to another student", HttpStatus.FORBIDDEN);
        }

        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if (exam.getProctoringMode() == ProctoringMode.NONE) {
            return ViolationResultDTO.builder()
                    .currentViolationCount(0)
                    .maxViolationsAllowed(exam.getMaxViolationsAllowed())
                    .isDisqualified(false)
                    .message("Exam is in NONE proctoring mode. Violation ignored.")
                    .build();
        }

        if (Boolean.TRUE.equals(attempt.getIsDisqualified()) || attempt.getStatus() == AttemptStatus.DISQUALIFIED) {
            return ViolationResultDTO.builder()
                    .currentViolationCount(attempt.getViolationCount() != null ? attempt.getViolationCount() : 0)
                    .maxViolationsAllowed(exam.getMaxViolationsAllowed())
                    .isDisqualified(true)
                    .message("Lượt thi đã bị đình chỉ trước đó.")
                    .build();
        }

        int newCount = (attempt.getViolationCount() != null ? attempt.getViolationCount() : 0) + 1;
        attempt.setViolationCount(newCount);

        ViolationSeverity severity = dto.getSeverity() != null ? dto.getSeverity() : ViolationSeverity.LOW;
        if (dto.getViolationType() == ViolationType.DEVTOOLS_OPENED || dto.getViolationType() == ViolationType.MULTIPLE_DISPLAY) {
            severity = ViolationSeverity.HIGH;
        }

        ExamViolationLog logItem = ExamViolationLog.builder()
                .attemptId(attemptId)
                .examId(exam.getExamId())
                .studentId(studentId)
                .violationType(dto.getViolationType())
                .severity(severity)
                .violationCountAtTime(newCount)
                .description(dto.getDescription())
                .metadataJson(dto.getMetadataJson())
                .createdAt(Instant.now())
                .build();
        examViolationLogRepository.save(logItem);

        boolean autoBanned = false;
        if (Boolean.TRUE.equals(exam.getAutoBanOnLimit()) && exam.getMaxViolationsAllowed() != null && newCount >= exam.getMaxViolationsAllowed()) {
            attempt.setStatus(AttemptStatus.DISQUALIFIED);
            attempt.setIsDisqualified(true);
            attempt.setDisqualifiedReason("Tự động đình chỉ do số lần vi phạm (" + newCount + ") vượt quá giới hạn cho phép (" + exam.getMaxViolationsAllowed() + ")");
            attempt.setDisqualifiedAt(Instant.now());
            autoBanned = true;

            ExamViolationLog autoBanLog = ExamViolationLog.builder()
                    .attemptId(attemptId)
                    .examId(exam.getExamId())
                    .studentId(studentId)
                    .violationType(ViolationType.OTHER)
                    .severity(ViolationSeverity.CRITICAL)
                    .violationCountAtTime(newCount)
                    .description("Hệ thống tự động đình chỉ bài thi do vượt ngưỡng vi phạm tối đa")
                    .createdAt(Instant.now())
                    .build();
            examViolationLogRepository.save(autoBanLog);
        }

        examAttemptRepository.save(attempt);

        String message = autoBanned
                ? "Bạn đã vượt quá số lần vi phạm cho phép. Bài thi đã bị tự động đình chỉ."
                : "Ghi nhận vi phạm thành công. Lần vi phạm thứ: " + newCount;

        return ViolationResultDTO.builder()
                .currentViolationCount(newCount)
                .maxViolationsAllowed(exam.getMaxViolationsAllowed())
                .isDisqualified(autoBanned)
                .message(message)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamViolationLogDTO> getAttemptViolations(UUID attemptId, UUID currentUserId, String role) {
        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));
        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if ("STUDENT".equalsIgnoreCase(role)) {
            if (!Objects.equals(attempt.getStudentId(), currentUserId)) {
                throw new CustomException("Access denied: You cannot view another student's violation logs", HttpStatus.FORBIDDEN);
            }
        } else if (!"ADMIN".equalsIgnoreCase(role)) {
            checkExamOwnership(exam, currentUserId);
        }

        User student = userRepository.findById(attempt.getStudentId()).orElse(null);
        UserProfile profile = userProfileRepository.findById(attempt.getStudentId()).orElse(null);
        String studentName = (profile != null && profile.getFullName() != null) ? profile.getFullName()
                : (student != null ? student.getUsername() : "Unknown");
        String studentCode = profile != null ? profile.getStudentCode() : null;

        List<ExamViolationLog> logs = examViolationLogRepository.findByAttemptIdOrderByCreatedAtAsc(attemptId);
        return logs.stream().map(l -> ExamViolationLogDTO.builder()
                .logId(l.getLogId())
                .attemptId(l.getAttemptId())
                .examId(l.getExamId())
                .studentId(l.getStudentId())
                .studentName(studentName)
                .studentCode(studentCode)
                .violationType(l.getViolationType())
                .severity(l.getSeverity())
                .violationCountAtTime(l.getViolationCountAtTime())
                .description(l.getDescription())
                .metadataJson(l.getMetadataJson())
                .createdAt(l.getCreatedAt())
                .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ExamAttemptDTO disqualifyAttempt(UUID examId, UUID attemptId, DisqualifyAttemptRequestDTO dto, UUID instructorId, String role) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equalsIgnoreCase(role)) {
            checkExamOwnership(exam, instructorId);
        }

        ExamAttempt attempt = examAttemptRepository.findByIdWithLock(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(attempt.getExamId(), examId)) {
            throw new CustomException("Attempt does not belong to this exam", HttpStatus.BAD_REQUEST);
        }

        String reason = dto != null && dto.getReason() != null && !dto.getReason().trim().isEmpty()
                ? dto.getReason().trim()
                : "Vi phạm quy chế thi (Giảng viên đình chỉ)";

        attempt.setStatus(AttemptStatus.DISQUALIFIED);
        attempt.setIsDisqualified(true);
        attempt.setDisqualifiedReason(reason);
        attempt.setDisqualifiedAt(Instant.now());
        attempt.setDisqualifiedBy(instructorId);

        int currentCount = (attempt.getViolationCount() != null ? attempt.getViolationCount() : 0) + 1;
        attempt.setViolationCount(currentCount);

        ExamViolationLog banLog = ExamViolationLog.builder()
                .attemptId(attemptId)
                .examId(examId)
                .studentId(attempt.getStudentId())
                .violationType(ViolationType.INSTRUCTOR_DISQUALIFIED)
                .severity(ViolationSeverity.CRITICAL)
                .violationCountAtTime(currentCount)
                .description("Giảng viên cấm thi: " + reason)
                .metadataJson("{\"instructorId\": \"" + instructorId + "\"}")
                .createdAt(Instant.now())
                .build();
        examViolationLogRepository.save(banLog);

        ExamAttempt saved = examAttemptRepository.save(attempt);
        log.info("Student {} attempt {} in exam {} was DISQUALIFIED by instructor {}. Reason: {}",
                saved.getStudentId(), attemptId, examId, instructorId, reason);

        return toAttemptDTO(saved);
    }

    @Override
    @Transactional
    public ExamAttemptDTO reinstateAttempt(UUID examId, UUID attemptId, UUID instructorId, String role) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equalsIgnoreCase(role)) {
            checkExamOwnership(exam, instructorId);
        }

        ExamAttempt attempt = examAttemptRepository.findByIdWithLock(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(attempt.getExamId(), examId)) {
            throw new CustomException("Attempt does not belong to this exam", HttpStatus.BAD_REQUEST);
        }

        if (attempt.getStatus() != AttemptStatus.DISQUALIFIED && !Boolean.TRUE.equals(attempt.getIsDisqualified())) {
            throw new CustomException("Bài thi này không ở trạng thái bị đình chỉ", HttpStatus.BAD_REQUEST);
        }

        // Hủy đình chỉ / cấm thi
        attempt.setIsDisqualified(false);
        attempt.setDisqualifiedReason(null);
        attempt.setDisqualifiedAt(null);
        attempt.setDisqualifiedBy(null);

        Instant now = Instant.now();
        Instant expiry = calculateAttemptExpiry(attempt, exam);
        boolean isExpired = (expiry != null && now.isAfter(expiry));

        ExamAttempt saved;
        if (isExpired) {
            // Đã hết thời gian làm bài của lượt thi (durationMinutes) hoặc kỳ thi đã kết thúc (endTime)
            // Tự động thu bài và chấm điểm theo các câu trả lời đã lưu của sinh viên (kể cả khi sinh viên đã rời trang)
            BigDecimal totalScore = calculateAndGradeAttemptAnswers(attemptId, examId);
            attempt.setStatus(AttemptStatus.GRADED);
            attempt.setSubmittedAt(expiry != null && expiry.isBefore(now) ? expiry : now);
            attempt.setTotalScore(totalScore);

            ExamViolationLog unbanLog = ExamViolationLog.builder()
                    .attemptId(attemptId)
                    .examId(examId)
                    .studentId(attempt.getStudentId())
                    .violationType(ViolationType.OTHER)
                    .severity(ViolationSeverity.LOW)
                    .violationCountAtTime(attempt.getViolationCount() != null ? attempt.getViolationCount() : 0)
                    .description("Giảng viên đã mở lại bài thi. Do lượt thi đã hết thời gian làm bài, hệ thống đã tự động thu bài và chấm điểm theo các câu trả lời đã lưu.")
                    .metadataJson("{\"instructorId\": \"" + instructorId + "\", \"autoSubmitted\": true, \"totalScore\": \"" + totalScore + "\"}")
                    .createdAt(now)
                    .build();
            examViolationLogRepository.save(unbanLog);

            saved = examAttemptRepository.save(attempt);
            log.info("Student {} attempt {} in exam {} was REINSTATED & AUTO-SUBMITTED (time expired) by instructor {}. Total score: {}",
                    saved.getStudentId(), attemptId, examId, instructorId, totalScore);

            try {
                notificationService.sendNotification(
                        saved.getStudentId(),
                        "Bài thi đã được mở lại và tự động nộp: " + exam.getTitle(),
                        "Giảng viên đã hủy đình chỉ bài thi " + exam.getTitle() + ". Do lượt thi đã hết thời gian làm bài, hệ thống đã tự động nộp và chấm điểm theo các câu trả lời đã lưu của bạn. Điểm số: " + totalScore + " điểm.",
                        com.vatly1.example.entity.enums.NotificationType.EXAM_GRADED,
                        saved.getAttemptId(),
                        "EXAM_ATTEMPT"
                );
            } catch (Exception e) {
                log.warn("Failed to send reinstate auto-submit notification: {}", e.getMessage());
            }
        } else {
            // Còn thời gian làm bài -> khôi phục trạng thái IN_PROGRESS để sinh viên tiếp tục làm bài
            attempt.setStatus(AttemptStatus.IN_PROGRESS);

            ExamViolationLog unbanLog = ExamViolationLog.builder()
                    .attemptId(attemptId)
                    .examId(examId)
                    .studentId(attempt.getStudentId())
                    .violationType(ViolationType.OTHER)
                    .severity(ViolationSeverity.LOW)
                    .violationCountAtTime(attempt.getViolationCount() != null ? attempt.getViolationCount() : 0)
                    .description("Giảng viên đã khôi phục quyền làm bài thi (Hủy đình chỉ). Thời gian làm bài vẫn còn hiệu lực.")
                    .metadataJson("{\"instructorId\": \"" + instructorId + "\"}")
                    .createdAt(now)
                    .build();
            examViolationLogRepository.save(unbanLog);

            saved = examAttemptRepository.save(attempt);
            log.info("Student {} attempt {} in exam {} was REINSTATED (in progress) by instructor {}",
                    saved.getStudentId(), attemptId, examId, instructorId);

            try {
                notificationService.sendNotification(
                        saved.getStudentId(),
                        "Quyền làm bài thi đã được khôi phục: " + exam.getTitle(),
                        "Giảng viên đã hủy đình chỉ bài thi " + exam.getTitle() + ". Bạn có thể tiếp tục làm bài trong thời gian còn lại.",
                        com.vatly1.example.entity.enums.NotificationType.ANNOUNCEMENT,
                        saved.getAttemptId(),
                        "EXAM_ATTEMPT"
                );
            } catch (Exception e) {
                log.warn("Failed to send reinstate notification: {}", e.getMessage());
            }
        }

        return toAttemptDTO(saved);
    }
}