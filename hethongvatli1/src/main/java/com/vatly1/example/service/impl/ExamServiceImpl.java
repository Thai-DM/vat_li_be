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
import com.vatly1.example.entity.ExamAnswer;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionOption;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.UserProfile;
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
import com.vatly1.example.repository.QuestionOptionRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    private final QuestionBankRepository questionBankRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final com.vatly1.example.service.ISystemSettingService systemSettingService;
    private final com.vatly1.example.service.INotificationService notificationService;
    private final com.vatly1.example.repository.IExamParticipantRepository examParticipantRepository;

    @Override
    @Transactional
    public ExamDTO createExam(CreateExamDTO dto, UUID creatorId) {
        Class clazz = classRepository.findById(dto.getClassId())
                .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));

        User creator = userRepository.findById(creatorId).orElse(null);
        boolean isAdmin = creator != null && creator.getRole() == UserRole.ADMIN;
        boolean isInstructor = Objects.equals(clazz.getInstructorId(), creatorId);
        boolean isStaff = classStaffRepository.existsByClassIdAndUserId(dto.getClassId(), creatorId);
        if (!isAdmin && !isInstructor && !isStaff) {
            throw new CustomException("Access denied: You are not authorized to manage exams for this class", HttpStatus.FORBIDDEN);
        }

        Exam exam = Exam.builder()
                .classId(dto.getClassId())
                .matrixId(dto.getMatrixId())
                .title(dto.getTitle())
                .examType(dto.getExamType())
                .durationMinutes(dto.getDurationMinutes())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .createdBy(creatorId)
                .createdAt(Instant.now())
                .build();

        Exam savedExam = examRepository.save(exam);

        if (dto.getMatrixId() != null) {
            populateQuestionsFromMatrix(savedExam.getExamId(), dto.getMatrixId());
        }

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

        return toExamDTO(savedExam);
    }

    @Override
    @Transactional
    public int autoGenerateQuestions(UUID examId, UUID instructorId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        checkExamOwnership(exam, instructorId);

        if (exam.getMatrixId() == null) {
            throw new CustomException("Exam has no associated matrix", HttpStatus.BAD_REQUEST);
        }

        return populateQuestionsFromMatrix(examId, exam.getMatrixId());
    }

    private int populateQuestionsFromMatrix(UUID examId, UUID matrixId) {
        List<ExamMatrixDetail> details = examMatrixDetailRepository.findByMatrixId(matrixId);
        int orderIndex = (int) examQuestionRepository.countByExamId(examId);
        int addedCount = 0;

        for (ExamMatrixDetail detail : details) {
            List<QuestionBank> questions = questionBankRepository.findByTopicIdAndDifficultyLevel(
                    detail.getTopicId(), detail.getDifficultyLevel());
            if (questions.isEmpty()) {
                questions = questionBankRepository.findByTopicId(detail.getTopicId());
            }

            int countForDetail = Math.min(detail.getNumQuestions(), questions.size());
            BigDecimal weight = detail.getWeightPercent() != null ? detail.getWeightPercent() : BigDecimal.ONE;

            for (int i = 0; i < countForDetail; i++) {
                QuestionBank q = questions.get(i);
                if (!examQuestionRepository.existsByExamIdAndQuestionId(examId, q.getQuestionId())) {
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
        }
        return addedCount;
    }

    @Override
    @Transactional
    public void addQuestionToExam(UUID examId, AddExamQuestionDTO dto, UUID instructorId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        checkExamOwnership(exam, instructorId);

        if (!questionBankRepository.existsById(dto.getQuestionId())) {
            throw new CustomException("Question not found in question bank", HttpStatus.NOT_FOUND);
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
            if (!classEnrollmentRepository.existsByClassIdAndStudentId(classId, currentUserId)) {
                throw new CustomException("Access denied: You are not enrolled in this class", HttpStatus.FORBIDDEN);
            }
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
            // STUDENT
            List<UUID> classIds = classRepository.findEnrolledClassIdsByStudentId(currentUserId);
            if (classIds.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            return examRepository.findByClassIdIn(classIds).stream()
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
    @Transactional
    public ExamAttemptDTO startAttempt(UUID examId, UUID studentId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));

        User studentUser = userRepository.findById(studentId).orElse(null);
        boolean isAdmin = studentUser != null && studentUser.getRole() == UserRole.ADMIN;
        if (!isAdmin) {
            boolean isEnrolled = classEnrollmentRepository.existsByClassIdAndStudentId(exam.getClassId(), studentId);
            boolean isTransferred = examParticipantRepository.existsByExamIdAndStudentId(examId, studentId);
            if (!isEnrolled && !isTransferred) {
                throw new CustomException("Bạn không có tên trong danh sách ca thi này", HttpStatus.FORBIDDEN);
            }
        }

        Instant now = Instant.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new CustomException("Exam has not started yet", HttpStatus.BAD_REQUEST);
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            throw new CustomException("Exam has already ended", HttpStatus.BAD_REQUEST);
        }

        int nextAttemptNumber = 1;

        if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.PRACTICE) {
            // For practice exams: check if student has an unfinished (IN_PROGRESS) attempt
            java.util.Optional<ExamAttempt> inProgress = examAttemptRepository.findFirstByExamIdAndStudentIdAndStatus(
                    examId, studentId, AttemptStatus.IN_PROGRESS);
            if (inProgress.isPresent()) {
                throw new CustomException("You already have an attempt in progress for this practice exam", HttpStatus.CONFLICT);
            }

            long existingCount = examAttemptRepository.countByExamIdAndStudentId(examId, studentId);
            int maxAttempts = 10;
            if (systemSettingService != null) {
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

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new CustomException("Cannot submit answer: Attempt is already completed", HttpStatus.BAD_REQUEST);
        }

        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        if (exam.getEndTime() != null && Instant.now().isAfter(exam.getEndTime())) {
            throw new CustomException("Cannot submit answer: Exam has already ended", HttpStatus.BAD_REQUEST);
        }

        if (!examQuestionRepository.existsByExamIdAndQuestionId(attempt.getExamId(), dto.getQuestionId())) {
            throw new CustomException("Question does not belong to this exam", HttpStatus.BAD_REQUEST);
        }

        ExamAnswer answer = examAnswerRepository.findByAttemptIdAndQuestionId(attemptId, dto.getQuestionId())
                .orElse(ExamAnswer.builder()
                        .attemptId(attemptId)
                        .questionId(dto.getQuestionId())
                        .build());

        answer.setSelectedOptionIds(dto.getSelectedOptionIds() != null ? new ArrayList<>(dto.getSelectedOptionIds()) : new ArrayList<>());
        answer.setAnswerText(dto.getAnswerText());
        examAnswerRepository.save(answer);
    }

    @Override
    @Transactional
    public ExamAttemptDTO submitAttempt(UUID attemptId, UUID studentId) {
        ExamAttempt attempt = examAttemptRepository.findByIdWithLock(attemptId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(attempt.getStudentId(), studentId)) {
            throw new CustomException("Access denied: This attempt belongs to another student", HttpStatus.FORBIDDEN);
        }

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new CustomException("Attempt is already submitted", HttpStatus.BAD_REQUEST);
        }

        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        if (exam.getEndTime() != null && Instant.now().isAfter(exam.getEndTime())) {
            throw new CustomException("Cannot submit exam: Exam deadline has passed", HttpStatus.BAD_REQUEST);
        }

        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(attempt.getExamId());
        List<ExamAnswer> answers = examAnswerRepository.findByAttemptId(attemptId);
        Map<UUID, ExamAnswer> answerMap = answers.stream()
                .collect(Collectors.toMap(ExamAnswer::getQuestionId, a -> a, (a1, a2) -> a1));

        BigDecimal totalScore = BigDecimal.ZERO;

        for (ExamQuestion eq : questions) {
            ExamAnswer ans = answerMap.get(eq.getQuestionId());
            List<QuestionOption> correctOptions = questionOptionRepository.findByQuestionIdAndIsCorrectTrue(eq.getQuestionId());
            Set<UUID> correctOptionIds = correctOptions.stream()
                    .map(QuestionOption::getOptionId)
                    .collect(Collectors.toSet());

            BigDecimal weight = eq.getScoreWeight() != null ? eq.getScoreWeight() : BigDecimal.ONE;

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

        attempt.setStatus(AttemptStatus.GRADED);
        attempt.setSubmittedAt(Instant.now());
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
            boolean isEnrolled = classEnrollmentRepository.existsByClassIdAndStudentId(exam.getClassId(), currentUserId);
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
            if (qb.getTopicId() != null) {
                Topic topic = topicRepository.findById(qb.getTopicId()).orElse(null);
                if (topic != null) {
                    topicName = topic.getTopicName();
                }
            }

            List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(qb.getQuestionId());
            List<QuestionOptionDTO> optionDTOs = options.stream()
                    .map(opt -> QuestionOptionDTO.builder()
                            .optionId(opt.getOptionId())
                            .questionId(opt.getQuestionId())
                            .content(opt.getOptionText())
                            .isCorrect(hideAnswers ? null : opt.getIsCorrect())
                            .orderIndex(opt.getOrderIndex())
                            .build())
                    .collect(Collectors.toList());

            results.add(ExamQuestionDetailDTO.builder()
                    .examId(examId)
                    .questionId(qb.getQuestionId())
                    .content(qb.getContent())
                    .questionType(qb.getQuestionType())
                    .difficultyLevel(qb.getDifficultyLevel())
                    .topicId(qb.getTopicId())
                    .topicName(topicName)
                    .orderIndex(eq.getOrderIndex())
                    .scoreWeight(eq.getScoreWeight())
                    .options(optionDTOs)
                    .build());
        }

        return results;
    }

    private List<StudentExamQuestionDTO> buildStudentQuestionsForAttempt(ExamAttempt attempt, Exam exam, String role) {
        boolean isGraded = attempt.getStatus() == AttemptStatus.GRADED;
        boolean canSeeAnswers = "ADMIN".equalsIgnoreCase(role) || "INSTRUCTOR".equalsIgnoreCase(role) || "TA".equalsIgnoreCase(role) || isGraded;

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(attempt.getExamId());
        List<ExamAnswer> studentAnswers = examAnswerRepository.findByAttemptId(attempt.getAttemptId());
        Map<UUID, ExamAnswer> answerMap = studentAnswers.stream()
                .collect(Collectors.toMap(ExamAnswer::getQuestionId, a -> a, (a1, a2) -> a1));

        List<StudentExamQuestionDTO> results = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) {
            QuestionBank qb = questionBankRepository.findById(eq.getQuestionId()).orElse(null);
            if (qb == null) continue;

            String topicName = null;
            if (qb.getTopicId() != null) {
                Topic topic = topicRepository.findById(qb.getTopicId()).orElse(null);
                if (topic != null) {
                    topicName = topic.getTopicName();
                }
            }

            List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(qb.getQuestionId());
            List<StudentQuestionOptionDTO> optionDTOs = options.stream()
                    .map(opt -> StudentQuestionOptionDTO.builder()
                            .optionId(opt.getOptionId())
                            .questionId(opt.getQuestionId())
                            .content(opt.getOptionText())
                            .orderIndex(opt.getOrderIndex())
                            .isCorrect(canSeeAnswers ? opt.getIsCorrect() : null)
                            .build())
                    .collect(Collectors.toList());

            ExamAnswer ans = answerMap.get(eq.getQuestionId());

            results.add(StudentExamQuestionDTO.builder()
                    .examId(exam.getExamId())
                    .questionId(qb.getQuestionId())
                    .content(qb.getContent())
                    .questionType(qb.getQuestionType())
                    .difficultyLevel(qb.getDifficultyLevel())
                    .topicId(qb.getTopicId())
                    .topicName(topicName)
                    .orderIndex(eq.getOrderIndex())
                    .scoreWeight(eq.getScoreWeight())
                    .options(optionDTOs)
                    .selectedOptionIds(ans != null && ans.getSelectedOptionIds() != null ? ans.getSelectedOptionIds() : new ArrayList<>())
                    .answerText(ans != null ? ans.getAnswerText() : null)
                    .isCorrect(isGraded && ans != null ? ans.getIsCorrect() : null)
                    .score(isGraded && ans != null ? ans.getScore() : null)
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

        if (examAttemptRepository.countByExamId(examId) > 0) {
            throw new CustomException("Cannot remove question from an exam that already has student attempts", HttpStatus.BAD_REQUEST);
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
        checkExamOwnership(exam, instructorId);

        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            exam.setTitle(dto.getTitle().trim());
        }
        if (dto.getExamType() != null) {
            exam.setExamType(dto.getExamType());
        }
        if (dto.getDurationMinutes() != null) {
            exam.setDurationMinutes(dto.getDurationMinutes());
        }
        if (dto.getStartTime() != null) {
            exam.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            exam.setEndTime(dto.getEndTime());
        }
        if (dto.getMatrixId() != null) {
            exam.setMatrixId(dto.getMatrixId());
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

        if (examAttemptRepository.countByExamId(examId) > 0) {
            throw new CustomException("Cannot delete exam that already has student attempts", HttpStatus.BAD_REQUEST);
        }

        examQuestionRepository.deleteByExamId(examId);
        examParticipantRepository.deleteByExamId(examId);
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

    private void checkExamOwnership(Exam exam, UUID instructorId) {
        Class clazz = classRepository.findById(exam.getClassId())
                .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));
        User user = userRepository.findById(instructorId).orElse(null);
        boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
        boolean isInstructor = Objects.equals(clazz.getInstructorId(), instructorId);
        boolean isStaff = classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), instructorId);
        if (!isAdmin && !isInstructor && !isStaff) {
            throw new CustomException("Access denied: You are not authorized to modify this exam", HttpStatus.FORBIDDEN);
        }
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
                .build();
    }

    private ExamAttemptDTO toAttemptDTO(ExamAttempt attempt) {
        return ExamAttemptDTO.builder()
                .attemptId(attempt.getAttemptId())
                .examId(attempt.getExamId())
                .studentId(attempt.getStudentId())
                .attemptNumber(attempt.getAttemptNumber())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .status(attempt.getStatus())
                .totalScore(attempt.getTotalScore())
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

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new CustomException("Cannot autosave answer: Attempt is already completed", HttpStatus.BAD_REQUEST);
        }

        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
        if (exam.getEndTime() != null && Instant.now().isAfter(exam.getEndTime())) {
            throw new CustomException("Cannot autosave answer: Exam has already ended", HttpStatus.BAD_REQUEST);
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
            boolean isEnrolled = classEnrollmentRepository.existsByClassIdAndStudentId(exam.getClassId(), studentId);
            boolean isTransferred = examParticipantRepository.existsByExamIdAndStudentId(examId, studentId);
            if (!isEnrolled && !isTransferred) {
                throw new CustomException("Bạn không có tên trong danh sách ca thi này", HttpStatus.FORBIDDEN);
            }
        }

        Instant now = Instant.now();
        boolean isStarted = exam.getStartTime() == null || !now.isBefore(exam.getStartTime());
        boolean isEnded = exam.getEndTime() != null && now.isAfter(exam.getEndTime());

        int maxAttempts = 1;
        if (exam.getExamType() == com.vatly1.example.entity.enums.ExamType.PRACTICE) {
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

        boolean canStart = isStarted && !isEnded && !hasInProgress && remainingAttempts > 0;

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
}