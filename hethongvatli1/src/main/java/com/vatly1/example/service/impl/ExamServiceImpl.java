package com.vatly1.example.service.impl;

import com.vatly1.example.model.request.AddExamQuestionDTO;
import com.vatly1.example.model.request.CreateExamDTO;
import com.vatly1.example.model.dto.ExamAttemptDTO;
import com.vatly1.example.model.dto.ExamDTO;
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
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.IExamAnswerRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamMatrixDetailRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
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
            return toAttemptDTO(saved);
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

        if ("STUDENT".equalsIgnoreCase(role)) {
            if (!Objects.equals(attempt.getStudentId(), currentUserId)) {
                throw new CustomException("Access denied: You cannot view another student's exam attempt", HttpStatus.FORBIDDEN);
            }
        } else if ("INSTRUCTOR".equalsIgnoreCase(role)) {
            Exam exam = examRepository.findById(attempt.getExamId())
                    .orElseThrow(() -> new CustomException("Exam not found", HttpStatus.NOT_FOUND));
            Class clazz = classRepository.findById(exam.getClassId())
                    .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));
            boolean isInstructor = Objects.equals(clazz.getInstructorId(), currentUserId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), currentUserId);
            if (!isInstructor && !isStaff) {
                throw new CustomException("Access denied: You are not authorized to view attempts for this class", HttpStatus.FORBIDDEN);
            }
        }

        return toAttemptDTO(attempt);
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
}