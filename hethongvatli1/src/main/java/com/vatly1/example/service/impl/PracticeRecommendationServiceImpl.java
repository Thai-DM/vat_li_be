package com.vatly1.example.service.impl;

import com.vatly1.example.entity.*;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.*;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.CreatePracticeRecommendationRequest;
import com.vatly1.example.model.request.SubmitSessionAnswerRequest;
import com.vatly1.example.repository.*;
import com.vatly1.example.service.IClassSettingsService;
import com.vatly1.example.service.IPracticeRecommendationService;
import com.vatly1.example.utils.AnswerNormalizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PracticeRecommendationServiceImpl implements IPracticeRecommendationService {

    public static final int MIN_ANSWERED_THRESHOLD = 3;
    public static final double NEEDS_PRACTICE_THRESHOLD = 0.60;
    public static final double IMPROVING_THRESHOLD = 0.80;

    private final IClassRepository classRepository;
    private final IClassEnrollmentRepository classEnrollmentRepository;
    private final IClassSettingsService classSettingsService;
    private final IClassStaffRepository classStaffRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionAcceptedAnswerRepository questionAcceptedAnswerRepository;
    private final TopicRepository topicRepository;
    private final IChapterRepository chapterRepository;
    private final IExamRepository examRepository;
    private final IExamQuestionRepository examQuestionRepository;
    private final IExamAttemptRepository examAttemptRepository;
    private final IExamAnswerRepository examAnswerRepository;
    private final IPracticeSessionRepository practiceSessionRepository;
    private final IPracticeSessionQuestionRepository practiceSessionQuestionRepository;
    private final IPracticeSessionAnswerRepository practiceSessionAnswerRepository;
    private final IUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WeakTopicDTO> getStudentWeakTopics(UUID classId, UUID studentId) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        UUID subjectId = clazz.getSubjectId();
        List<Topic> topics = topicRepository.findBySubjectIdOrderByOrderIndexAsc(subjectId);
        if (topics.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, String> chapterNameMap = new HashMap<>();
        for (Topic t : topics) {
            if (t.getChapterId() != null && !chapterNameMap.containsKey(t.getChapterId())) {
                chapterRepository.findById(t.getChapterId()).ifPresent(ch -> chapterNameMap.put(ch.getChapterId(), ch.getChapterName()));
            }
        }

        // 1. Thu thập câu trả lời từ các bài thi chính thức (ExamAnswer) đã hoàn tất, không bao gồm đề FINAL
        Map<UUID, Boolean> latestAnswerPerQuestion = new HashMap<>();
        Map<UUID, UUID> questionTopicMap = new HashMap<>();

        List<ExamAttempt> attempts = examAttemptRepository.findByStudentId(studentId);
        List<ExamAttempt> completedAttempts = attempts.stream()
                .filter(a -> a.getStatus() == AttemptStatus.GRADED || a.getStatus() == AttemptStatus.SUBMITTED)
                .sorted(Comparator.comparing(ExamAttempt::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        for (ExamAttempt att : completedAttempts) {
            Exam exam = examRepository.findById(att.getExamId()).orElse(null);
            if (exam == null || exam.getExamType() == ExamType.FINAL || exam.getClassId() == null) {
                continue;
            }
            Class examClass = classRepository.findById(exam.getClassId()).orElse(null);
            if (examClass == null || !subjectId.equals(examClass.getSubjectId())) {
                continue;
            }

            List<ExamAnswer> answers = examAnswerRepository.findByAttemptId(att.getAttemptId());
            for (ExamAnswer ans : answers) {
                if (ans.getIsCorrect() == null) continue;
                UUID qId = ans.getQuestionId();
                populateQuestionTopic(qId, subjectId, questionTopicMap);
                if (questionTopicMap.containsKey(qId)) {
                    latestAnswerPerQuestion.put(qId, ans.getIsCorrect());
                }
            }
        }

        // 2. Thu thập câu trả lời từ các phiên luyện tập cá nhân (PracticeSessionAnswer) đã COMPLETED
        List<PracticeSession> completedSessions = practiceSessionRepository.findByStudentIdAndSubjectIdAndStatusOrderByStartedAtDesc(
                studentId, subjectId, PracticeSessionStatus.COMPLETED);
        // Duyệt theo thứ tự thời gian tăng dần để câu trả lời mới nhất ghi đè
        List<PracticeSession> chronologicalSessions = new ArrayList<>(completedSessions);
        Collections.reverse(chronologicalSessions);

        for (PracticeSession session : chronologicalSessions) {
            List<PracticeSessionAnswer> sessionAnswers = practiceSessionAnswerRepository.findBySessionId(session.getSessionId());
            for (PracticeSessionAnswer ans : sessionAnswers) {
                if (ans.getIsCorrect() == null) continue;
                UUID qId = ans.getQuestionId();
                populateQuestionTopic(qId, subjectId, questionTopicMap);
                if (questionTopicMap.containsKey(qId)) {
                    latestAnswerPerQuestion.put(qId, ans.getIsCorrect());
                }
            }
        }

        // 3. Gom nhóm theo Topic
        Map<UUID, Integer> totalPerTopic = new HashMap<>();
        Map<UUID, Integer> correctPerTopic = new HashMap<>();

        for (Map.Entry<UUID, Boolean> entry : latestAnswerPerQuestion.entrySet()) {
            UUID qId = entry.getKey();
            Boolean isCorrect = entry.getValue();
            UUID topicId = questionTopicMap.get(qId);
            if (topicId != null) {
                totalPerTopic.merge(topicId, 1, Integer::sum);
                if (Boolean.TRUE.equals(isCorrect)) {
                    correctPerTopic.merge(topicId, 1, Integer::sum);
                }
            }
        }

        // 4. Xây dựng DTO cho từng Topic
        List<WeakTopicDTO> results = new ArrayList<>();
        for (Topic t : topics) {
            int total = totalPerTopic.getOrDefault(t.getTopicId(), 0);
            int correct = correctPerTopic.getOrDefault(t.getTopicId(), 0);
            int wrong = total - correct;
            double accuracy = (total > 0) ? (double) correct / total : 0.0;

            WeaknessLevel level;
            String rec;
            boolean hasEnoughData = (total >= MIN_ANSWERED_THRESHOLD);

            if (!hasEnoughData) {
                level = WeaknessLevel.INSUFFICIENT_DATA;
                rec = String.format("Chưa đủ dữ liệu đánh giá (mới làm %d/%d câu tối thiểu)", total, MIN_ANSWERED_THRESHOLD);
            } else if (accuracy < NEEDS_PRACTICE_THRESHOLD) {
                level = WeaknessLevel.NEEDS_PRACTICE;
                rec = String.format("Chủ đề cần ưu tiên luyện tập ngay do tỷ lệ sai cao (Độ chính xác %.1f%%)", accuracy * 100);
            } else if (accuracy < IMPROVING_THRESHOLD) {
                level = WeaknessLevel.IMPROVING;
                rec = String.format("Chủ đề cần củng cố thêm kiến thức (Độ chính xác %.1f%%)", accuracy * 100);
            } else {
                level = WeaknessLevel.GOOD;
                rec = String.format("Chủ đề đã nắm vững kiến thức (Độ chính xác %.1f%%)", accuracy * 100);
            }

            results.add(WeakTopicDTO.builder()
                    .topicId(t.getTopicId())
                    .topicName(t.getTopicName())
                    .chapterId(t.getChapterId())
                    .chapterName(chapterNameMap.get(t.getChapterId()))
                    .totalAnswered(total)
                    .correctCount(correct)
                    .wrongCount(wrong)
                    .accuracyRate(Math.round(accuracy * 10000.0) / 10000.0)
                    .weaknessLevel(level)
                    .hasEnoughData(hasEnoughData)
                    .recommendation(rec)
                    .build());
        }

        // 5. Sắp xếp thứ tự ưu tiên: NEEDS_PRACTICE -> IMPROVING -> INSUFFICIENT_DATA -> GOOD
        results.sort(Comparator
                .comparingInt((WeakTopicDTO w) -> getLevelPriority(w.getWeaknessLevel()))
                .thenComparingDouble(WeakTopicDTO::getAccuracyRate)
                .thenComparing((WeakTopicDTO w) -> -w.getWrongCount()));

        return results;
    }

    private int getLevelPriority(WeaknessLevel level) {
        if (level == WeaknessLevel.NEEDS_PRACTICE) return 1;
        if (level == WeaknessLevel.IMPROVING) return 2;
        if (level == WeaknessLevel.INSUFFICIENT_DATA) return 3;
        return 4; // GOOD
    }

    private void populateQuestionTopic(UUID questionId, UUID subjectId, Map<UUID, UUID> questionTopicMap) {
        if (!questionTopicMap.containsKey(questionId)) {
            questionBankRepository.findById(questionId).ifPresent(qb -> {
                if (subjectId.equals(qb.getSubjectId()) && qb.getTopicId() != null) {
                    questionTopicMap.put(questionId, qb.getTopicId());
                }
            });
        }
    }

    @Override
    @Transactional
    public PracticeSessionDTO createAdaptivePracticeSession(UUID classId, CreatePracticeRecommendationRequest request, UUID studentId) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        User user = userRepository.findById(studentId).orElse(null);
        boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
        if (!isAdmin) {
            boolean isEnrolled = classEnrollmentRepository.isStudentActivelyEnrolledInClass(classId, studentId);
            if (!isEnrolled) {
                throw new CustomException("Bạn không phải học sinh của lớp học này hoặc chưa được kích hoạt ghi danh", HttpStatus.FORBIDDEN);
            }
        }

        // Kiểm tra cấu hình lớp học
        ClassSettings settings = classSettingsService.getEffectiveSettings(classId);
        if (!Boolean.TRUE.equals(settings.getPracticeEnabled())) {
            throw new CustomException("Hoạt động luyện tập hiện đang bị khóa đối với lớp này", HttpStatus.BAD_REQUEST);
        }

        int requestedCount = (request != null && request.getQuestionCount() != null) ? request.getQuestionCount() : 10;
        if (requestedCount <= 0 || requestedCount > 50) {
            requestedCount = 10;
        }

        UUID subjectId = clazz.getSubjectId();

        // Danh sách các câu hỏi FINAL phải tuyệt đối loại trừ
        List<UUID> finalExamQuestionIds = examQuestionRepository.findDistinctQuestionIdsInFinalExams();
        Set<UUID> finalExcludedSet = new HashSet<>(finalExamQuestionIds);

        // Lấy danh sách điểm yếu của học sinh
        List<WeakTopicDTO> weakTopics = getStudentWeakTopics(classId, studentId);

        // Lịch sử câu hỏi học sinh đã làm và làm sai
        Set<UUID> answeredQuestionIds = getAnsweredQuestionIds(studentId, subjectId);
        Set<UUID> wrongQuestionIds = getWrongQuestionIds(studentId, subjectId);

        // Xác định các Topic mục tiêu ưu tiên
        List<UUID> targetTopicIds = new ArrayList<>();
        boolean isSpecificTopic = false;

        if (request != null && request.getTopicId() != null) {
            UUID reqTopicId = request.getTopicId();
            Topic reqTopic = topicRepository.findById(reqTopicId)
                    .orElseThrow(() -> new CustomException("Không tìm thấy chủ đề yêu cầu", HttpStatus.NOT_FOUND));
            if (!subjectId.equals(reqTopic.getSubjectId())) {
                throw new CustomException("Chủ đề không thuộc môn học của lớp này", HttpStatus.BAD_REQUEST);
            }
            targetTopicIds.add(reqTopicId);
            isSpecificTopic = true;
        } else {
            // Ưu tiên 1: Các topic NEEDS_PRACTICE
            targetTopicIds = weakTopics.stream()
                    .filter(w -> w.getWeaknessLevel() == WeaknessLevel.NEEDS_PRACTICE)
                    .map(WeakTopicDTO::getTopicId)
                    .collect(Collectors.toList());

            // Ưu tiên 2: Nếu không có NEEDS_PRACTICE, lấy các topic IMPROVING
            if (targetTopicIds.isEmpty()) {
                targetTopicIds = weakTopics.stream()
                        .filter(w -> w.getWeaknessLevel() == WeaknessLevel.IMPROVING)
                        .map(WeakTopicDTO::getTopicId)
                        .collect(Collectors.toList());
            }

            // Nếu cả 2 đều không có (tất cả là GOOD hoặc INSUFFICIENT_DATA):
            if (targetTopicIds.isEmpty()) {
                targetTopicIds = weakTopics.stream()
                        .map(WeakTopicDTO::getTopicId)
                        .collect(Collectors.toList());
            }
        }

        List<QuestionBank> selectedQuestions = new ArrayList<>();
        Set<UUID> selectedQuestionIds = new HashSet<>();
        Map<UUID, Boolean> isReviewedMap = new HashMap<>();

        // 1. Phân bổ câu hỏi theo từng Topic mục tiêu
        for (UUID topicId : targetTopicIds) {
            if (selectedQuestions.size() >= requestedCount) break;

            List<QuestionBank> candidates = questionBankRepository.findByTopicIdAndApprovalStatus(topicId, ApprovalStatus.APPROVED)
                    .stream()
                    .filter(q -> subjectId.equals(q.getSubjectId()))
                    .filter(q -> q.getQuestionType() == QuestionType.MCQ_SINGLE || q.getQuestionType() == QuestionType.FILL_BLANK)
                    .filter(q -> !finalExcludedSet.contains(q.getQuestionId()))
                    .filter(q -> !selectedQuestionIds.contains(q.getQuestionId()))
                    .collect(Collectors.toList());

            // Ưu tiên câu học sinh CHƯA TỪNG LÀM
            List<QuestionBank> freshQuestions = candidates.stream()
                    .filter(q -> !answeredQuestionIds.contains(q.getQuestionId()))
                    .collect(Collectors.toList());

            // Kế tiếp là câu học sinh ĐÃ TỪNG LÀM SAI (để ôn tập lại)
            List<QuestionBank> reviewQuestions = candidates.stream()
                    .filter(q -> wrongQuestionIds.contains(q.getQuestionId()))
                    .collect(Collectors.toList());

            for (QuestionBank q : freshQuestions) {
                if (selectedQuestions.size() >= requestedCount) break;
                selectedQuestions.add(q);
                selectedQuestionIds.add(q.getQuestionId());
                isReviewedMap.put(q.getQuestionId(), false);
            }

            for (QuestionBank q : reviewQuestions) {
                if (selectedQuestions.size() >= requestedCount) break;
                if (!selectedQuestionIds.contains(q.getQuestionId())) {
                    selectedQuestions.add(q);
                    selectedQuestionIds.add(q.getQuestionId());
                    isReviewedMap.put(q.getQuestionId(), true);
                }
            }
        }

        boolean expandedTopicsUsed = false;
        String expansionReason = null;
        boolean allowExpanded = request != null && Boolean.TRUE.equals(request.getAllowExpandedSubjectTopics());

        // 2. CHÍNH SÁCH FALLBACK:
        // Mặc định CHỈ lấy câu thuộc Topic đã được xác định cần ôn tập.
        // Chỉ khi học sinh BẬT RÕ RÀNG tùy chọn allowExpandedSubjectTopics thì mới mở rộng sang các Topic khác.
        if (selectedQuestions.size() < requestedCount && allowExpanded) {
            List<QuestionBank> allSubjectQuestions = questionBankRepository.findBySubjectIdAndApprovalStatus(subjectId, ApprovalStatus.APPROVED)
                    .stream()
                    .filter(q -> q.getQuestionType() == QuestionType.MCQ_SINGLE || q.getQuestionType() == QuestionType.FILL_BLANK)
                    .filter(q -> !finalExcludedSet.contains(q.getQuestionId()))
                    .filter(q -> !selectedQuestionIds.contains(q.getQuestionId()))
                    .collect(Collectors.toList());

            int beforeExpand = selectedQuestions.size();
            for (QuestionBank q : allSubjectQuestions) {
                if (selectedQuestions.size() >= requestedCount) break;
                selectedQuestions.add(q);
                selectedQuestionIds.add(q.getQuestionId());
                isReviewedMap.put(q.getQuestionId(), false);
            }
            if (selectedQuestions.size() > beforeExpand) {
                expandedTopicsUsed = true;
                expansionReason = String.format("Đã bổ sung thêm %d câu hỏi từ các chủ đề khác trong môn học theo tùy chọn ôn tập mở rộng", selectedQuestions.size() - beforeExpand);
            }
        }

        if (selectedQuestions.isEmpty()) {
            throw new CustomException("Chủ đề cần ôn tập hiện không có câu hỏi hợp lệ (APPROVED) trong ngân hàng câu hỏi để tạo bài luyện tập", HttpStatus.BAD_REQUEST);
        }

        boolean isDeficient = (selectedQuestions.size() < requestedCount);
        String deficiencyNotice = null;
        if (isDeficient) {
            deficiencyNotice = String.format("Chủ đề cần ôn tập chỉ có %d/%d câu hỏi khả dụng trong ngân hàng câu hỏi.", selectedQuestions.size(), requestedCount);
        }

        // Tạo phiên luyện tập
        PracticeSession session = PracticeSession.builder()
                .studentId(studentId)
                .classId(classId)
                .subjectId(subjectId)
                .totalQuestions(selectedQuestions.size())
                .status(PracticeSessionStatus.IN_PROGRESS)
                .totalScore(BigDecimal.ZERO)
                .accuracyRate(BigDecimal.ZERO)
                .startedAt(Instant.now())
                .build();

        PracticeSession savedSession = practiceSessionRepository.save(session);

        int order = 1;
        for (QuestionBank q : selectedQuestions) {
            PracticeSessionQuestion psq = PracticeSessionQuestion.builder()
                    .sessionId(savedSession.getSessionId())
                    .questionId(q.getQuestionId())
                    .topicId(q.getTopicId())
                    .orderIndex(order++)
                    .isReviewed(isReviewedMap.getOrDefault(q.getQuestionId(), false))
                    .build();
            practiceSessionQuestionRepository.save(psq);
        }

        log.info("Created adaptive practice session {} for student {} with {} questions (Target topics: {}, Deficient: {}, Expanded: {})",
                savedSession.getSessionId(), studentId, selectedQuestions.size(), targetTopicIds, isDeficient, expandedTopicsUsed);

        PracticeSessionDTO dto = toSessionDTO(savedSession, false);
        dto.setIsDeficient(isDeficient);
        dto.setDeficiencyNotice(deficiencyNotice);
        dto.setExpandedTopicsUsed(expandedTopicsUsed);
        dto.setExpansionReason(expansionReason);
        return dto;
    }

    private Set<UUID> getAnsweredQuestionIds(UUID studentId, UUID subjectId) {
        Set<UUID> set = new HashSet<>();
        List<ExamAttempt> attempts = examAttemptRepository.findByStudentId(studentId);
        for (ExamAttempt att : attempts) {
            List<ExamAnswer> answers = examAnswerRepository.findByAttemptId(att.getAttemptId());
            for (ExamAnswer a : answers) {
                set.add(a.getQuestionId());
            }
        }
        List<PracticeSession> sessions = practiceSessionRepository.findByStudentIdAndSubjectIdAndStatusOrderByStartedAtDesc(
                studentId, subjectId, PracticeSessionStatus.COMPLETED);
        for (PracticeSession s : sessions) {
            List<PracticeSessionAnswer> answers = practiceSessionAnswerRepository.findBySessionId(s.getSessionId());
            for (PracticeSessionAnswer a : answers) {
                set.add(a.getQuestionId());
            }
        }
        return set;
    }

    private Set<UUID> getWrongQuestionIds(UUID studentId, UUID subjectId) {
        Set<UUID> set = new HashSet<>();
        List<ExamAttempt> attempts = examAttemptRepository.findByStudentId(studentId);
        for (ExamAttempt att : attempts) {
            List<ExamAnswer> answers = examAnswerRepository.findByAttemptId(att.getAttemptId());
            for (ExamAnswer a : answers) {
                if (Boolean.FALSE.equals(a.getIsCorrect())) {
                    set.add(a.getQuestionId());
                }
            }
        }
        List<PracticeSession> sessions = practiceSessionRepository.findByStudentIdAndSubjectIdAndStatusOrderByStartedAtDesc(
                studentId, subjectId, PracticeSessionStatus.COMPLETED);
        for (PracticeSession s : sessions) {
            List<PracticeSessionAnswer> answers = practiceSessionAnswerRepository.findBySessionId(s.getSessionId());
            for (PracticeSessionAnswer a : answers) {
                if (Boolean.FALSE.equals(a.getIsCorrect())) {
                    set.add(a.getQuestionId());
                }
            }
        }
        return set;
    }

    @Override
    @Transactional(readOnly = true)
    public PracticeSessionDTO getPracticeSession(UUID sessionId, UUID studentId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException("Không tìm thấy phiên luyện tập", HttpStatus.NOT_FOUND));

        if (!Objects.equals(session.getStudentId(), studentId)) {
            throw new CustomException("Bạn không có quyền truy cập phiên luyện tập của người khác", HttpStatus.FORBIDDEN);
        }

        boolean canSeeSolutions = false;
        if (session.getStatus() == PracticeSessionStatus.COMPLETED) {
            ClassSettings settings = classSettingsService.getEffectiveSettings(session.getClassId());
            canSeeSolutions = Boolean.TRUE.equals(settings.getPracticeShowSolution());
        }

        return toSessionDTO(session, canSeeSolutions);
    }

    @Override
    @Transactional
    public void saveAnswer(UUID sessionId, SubmitSessionAnswerRequest request, UUID studentId) {
        PracticeSession session = practiceSessionRepository.findByIdWithLock(sessionId)
                .orElseThrow(() -> new CustomException("Không tìm thấy phiên luyện tập", HttpStatus.NOT_FOUND));

        if (!Objects.equals(session.getStudentId(), studentId)) {
            throw new CustomException("Bạn không có quyền thao tác trên phiên luyện tập của người khác", HttpStatus.FORBIDDEN);
        }

        if (session.getStatus() != PracticeSessionStatus.IN_PROGRESS) {
            throw new CustomException("Phiên luyện tập đã hoàn thành hoặc kết thúc, không thể thay đổi đáp án", HttpStatus.BAD_REQUEST);
        }

        if (!practiceSessionQuestionRepository.existsBySessionIdAndQuestionId(sessionId, request.getQuestionId())) {
            throw new CustomException("Câu hỏi không thuộc phiên luyện tập này", HttpStatus.BAD_REQUEST);
        }

        PracticeSessionAnswer answer = practiceSessionAnswerRepository.findBySessionIdAndQuestionId(sessionId, request.getQuestionId())
                .orElse(PracticeSessionAnswer.builder()
                        .sessionId(sessionId)
                        .questionId(request.getQuestionId())
                        .build());

        QuestionBank qb = questionBankRepository.findById(request.getQuestionId()).orElse(null);
        if (qb != null && qb.getQuestionType() == QuestionType.FILL_BLANK) {
            answer.setAnswerText(request.getAnswerText());
            answer.setSelectedOptionIds(null);
        } else {
            if (request.getSelectedOptionIds() != null && !request.getSelectedOptionIds().isEmpty()) {
                for (UUID optId : request.getSelectedOptionIds()) {
                    if (!questionOptionRepository.existsByOptionIdAndQuestionId(optId, request.getQuestionId())) {
                        throw new CustomException("Lựa chọn không hợp lệ cho câu hỏi này", HttpStatus.BAD_REQUEST);
                    }
                }
                String joined = request.getSelectedOptionIds().stream().map(UUID::toString).collect(Collectors.joining(","));
                answer.setSelectedOptionIds(joined);
            } else {
                answer.setSelectedOptionIds(null);
            }
            answer.setAnswerText(null);
        }
        answer.setAnsweredAt(Instant.now());

        practiceSessionAnswerRepository.save(answer);
    }

    @Override
    @Transactional
    public PracticeSessionResultDTO submitSession(UUID sessionId, UUID studentId) {
        PracticeSession session = practiceSessionRepository.findByIdWithLock(sessionId)
                .orElseThrow(() -> new CustomException("Không tìm thấy phiên luyện tập", HttpStatus.NOT_FOUND));

        if (!Objects.equals(session.getStudentId(), studentId)) {
            throw new CustomException("Bạn không có quyền nộp bài cho phiên luyện tập của người khác", HttpStatus.FORBIDDEN);
        }

        if (session.getStatus() != PracticeSessionStatus.IN_PROGRESS) {
            throw new CustomException("Phiên luyện tập đã nộp bài trước đó, không thể nộp lại", HttpStatus.BAD_REQUEST);
        }

        List<PracticeSessionQuestion> sessionQuestions = practiceSessionQuestionRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
        List<PracticeSessionAnswer> existingAnswers = practiceSessionAnswerRepository.findBySessionId(sessionId);
        Map<UUID, PracticeSessionAnswer> answerMap = existingAnswers.stream()
                .collect(Collectors.toMap(PracticeSessionAnswer::getQuestionId, a -> a, (a1, a2) -> a1));

        int correctCount = 0;
        BigDecimal totalScore = BigDecimal.ZERO;

        for (PracticeSessionQuestion sq : sessionQuestions) {
            UUID qId = sq.getQuestionId();
            QuestionBank qb = questionBankRepository.findById(qId).orElse(null);
            PracticeSessionAnswer ans = answerMap.get(qId);
            if (ans == null) {
                ans = PracticeSessionAnswer.builder()
                        .sessionId(sessionId)
                        .questionId(qId)
                        .answeredAt(Instant.now())
                        .build();
            }

            boolean isCorrect = false;

            if (qb != null && qb.getQuestionType() == QuestionType.FILL_BLANK) {
                List<QuestionAcceptedAnswer> acceptedAnswers = questionAcceptedAnswerRepository.findByQuestionIdOrderByOrderIndexAsc(qId);
                List<String> acceptedTexts = acceptedAnswers.stream()
                        .map(QuestionAcceptedAnswer::getAcceptedText)
                        .collect(Collectors.toList());

                String studentText = ans.getAnswerText();
                isCorrect = AnswerNormalizationUtil.isMatchAny(studentText, acceptedTexts);
            } else if (qb != null) {
                List<QuestionOption> correctOptions = questionOptionRepository.findByQuestionIdAndIsCorrectTrue(qId);
                Set<UUID> correctIds = correctOptions.stream().map(QuestionOption::getOptionId).collect(Collectors.toSet());

                Set<UUID> studentIds = parseOptionIds(ans.getSelectedOptionIds());
                isCorrect = !correctIds.isEmpty() && studentIds.equals(correctIds);
            }

            ans.setIsCorrect(isCorrect);
            BigDecimal score = isCorrect ? BigDecimal.ONE : BigDecimal.ZERO;
            ans.setScore(score);
            practiceSessionAnswerRepository.save(ans);

            if (isCorrect) {
                correctCount++;
                totalScore = totalScore.add(score);
            }
        }

        int totalQuestions = sessionQuestions.size();
        int wrongCount = totalQuestions - correctCount;
        double accuracy = (totalQuestions > 0) ? (double) correctCount / totalQuestions : 0.0;

        session.setStatus(PracticeSessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        session.setTotalScore(totalScore);
        session.setAccuracyRate(BigDecimal.valueOf(accuracy).setScale(4, RoundingMode.HALF_UP));
        practiceSessionRepository.save(session);

        ClassSettings settings = classSettingsService.getEffectiveSettings(session.getClassId());
        boolean canSeeSolutions = Boolean.TRUE.equals(settings.getPracticeShowSolution());

        PracticeSessionDTO sessionDTO = toSessionDTO(session, canSeeSolutions);
        List<WeakTopicDTO> updatedWeakTopics = getStudentWeakTopics(session.getClassId(), studentId);

        return PracticeSessionResultDTO.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus())
                .totalQuestions(totalQuestions)
                .correctCount(correctCount)
                .wrongCount(wrongCount)
                .totalScore(totalScore)
                .accuracyRate(session.getAccuracyRate())
                .questions(sessionDTO.getQuestions())
                .updatedWeakTopics(updatedWeakTopics)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PracticeSessionResultDTO getSessionResult(UUID sessionId, UUID studentId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException("Không tìm thấy phiên luyện tập", HttpStatus.NOT_FOUND));

        if (!Objects.equals(session.getStudentId(), studentId)) {
            throw new CustomException("Bạn không có quyền xem kết quả của người khác", HttpStatus.FORBIDDEN);
        }

        if (session.getStatus() != PracticeSessionStatus.COMPLETED) {
            throw new CustomException("Phiên luyện tập chưa được hoàn thành", HttpStatus.BAD_REQUEST);
        }

        ClassSettings settings = classSettingsService.getEffectiveSettings(session.getClassId());
        boolean canSeeSolutions = Boolean.TRUE.equals(settings.getPracticeShowSolution());

        PracticeSessionDTO sessionDTO = toSessionDTO(session, canSeeSolutions);
        List<WeakTopicDTO> updatedWeakTopics = getStudentWeakTopics(session.getClassId(), studentId);

        List<PracticeSessionQuestion> sessionQuestions = practiceSessionQuestionRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
        List<PracticeSessionAnswer> answers = practiceSessionAnswerRepository.findBySessionId(sessionId);
        int correctCount = (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
        int totalQuestions = sessionQuestions.size();

        return PracticeSessionResultDTO.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus())
                .totalQuestions(totalQuestions)
                .correctCount(correctCount)
                .wrongCount(totalQuestions - correctCount)
                .totalScore(session.getTotalScore())
                .accuracyRate(session.getAccuracyRate())
                .questions(sessionDTO.getQuestions())
                .updatedWeakTopics(updatedWeakTopics)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassWeakTopicsSummaryDTO getClassWeakTopicsSummary(UUID classId, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUserRole);
        boolean isOwner = Objects.equals(clazz.getInstructorId(), currentUserId);
        boolean isStaff = classStaffRepository.existsByClassIdAndUserIdAndRoleInClass(classId, currentUserId, ClassStaffRole.INSTRUCTOR);
        if (!isAdmin && !isOwner && !isStaff) {
            throw new CustomException("Bạn không có quyền xem phân tích lớp học này", HttpStatus.FORBIDDEN);
        }

        List<ClassEnrollment> activeEnrollments = classEnrollmentRepository.findByClassIdAndStatus(classId, EnrollmentStatus.ACTIVE);
        int totalEnrolledStudents = activeEnrollments.size();

        List<Topic> topics = topicRepository.findBySubjectIdOrderByOrderIndexAsc(clazz.getSubjectId());
        Map<UUID, String> chapterNameMap = new HashMap<>();
        for (Topic t : topics) {
            if (t.getChapterId() != null && !chapterNameMap.containsKey(t.getChapterId())) {
                chapterRepository.findById(t.getChapterId()).ifPresent(ch -> chapterNameMap.put(ch.getChapterId(), ch.getChapterName()));
            }
        }

        // Tính toán ma trận điểm yếu của từng học sinh
        Map<UUID, List<WeakTopicDTO>> studentWeakMap = new HashMap<>();
        for (ClassEnrollment ce : activeEnrollments) {
            studentWeakMap.put(ce.getStudentId(), getStudentWeakTopics(classId, ce.getStudentId()));
        }

        List<TopicWeaknessSummaryDTO> topicSummaries = new ArrayList<>();
        for (Topic t : topics) {
            int studentsWithData = 0;
            int studentsNeedingPractice = 0;
            double sumAccuracy = 0.0;

            for (ClassEnrollment ce : activeEnrollments) {
                List<WeakTopicDTO> list = studentWeakMap.get(ce.getStudentId());
                if (list != null) {
                    for (WeakTopicDTO wt : list) {
                        if (wt.getTopicId().equals(t.getTopicId()) && wt.isHasEnoughData()) {
                            studentsWithData++;
                            sumAccuracy += wt.getAccuracyRate();
                            if (wt.getWeaknessLevel() == WeaknessLevel.NEEDS_PRACTICE) {
                                studentsNeedingPractice++;
                            }
                        }
                    }
                }
            }

            double needPracticePercentage = (studentsWithData > 0)
                    ? (double) studentsNeedingPractice * 100.0 / studentsWithData
                    : 0.0;
            double avgAccuracy = (studentsWithData > 0)
                    ? sumAccuracy / studentsWithData
                    : 0.0;

            topicSummaries.add(TopicWeaknessSummaryDTO.builder()
                    .topicId(t.getTopicId())
                    .topicName(t.getTopicName())
                    .chapterId(t.getChapterId())
                    .chapterName(chapterNameMap.get(t.getChapterId()))
                    .totalStudentsWithData(studentsWithData)
                    .studentsNeedingPractice(studentsNeedingPractice)
                    .needPracticePercentage(Math.round(needPracticePercentage * 100.0) / 100.0)
                    .averageAccuracyRate(Math.round(avgAccuracy * 10000.0) / 10000.0)
                    .build());
        }

        // Sắp xếp các topic có tỷ lệ học sinh cần củng cố cao nhất lên đầu
        topicSummaries.sort(Comparator.comparingDouble(TopicWeaknessSummaryDTO::getNeedPracticePercentage).reversed());

        return ClassWeakTopicsSummaryDTO.builder()
                .classId(clazz.getClassId())
                .classCode(clazz.getClassCode())
                .subjectId(clazz.getSubjectId())
                .totalEnrolledStudents(totalEnrolledStudents)
                .topics(topicSummaries)
                .build();
    }

    private PracticeSessionDTO toSessionDTO(PracticeSession session, boolean canSeeSolutions) {
        List<PracticeSessionQuestion> sessionQuestions = practiceSessionQuestionRepository.findBySessionIdOrderByOrderIndexAsc(session.getSessionId());
        List<PracticeSessionAnswer> sessionAnswers = practiceSessionAnswerRepository.findBySessionId(session.getSessionId());
        Map<UUID, PracticeSessionAnswer> answerMap = sessionAnswers.stream()
                .collect(Collectors.toMap(PracticeSessionAnswer::getQuestionId, a -> a, (a1, a2) -> a1));

        List<PracticeSessionQuestionDTO> questionDTOs = new ArrayList<>();
        for (PracticeSessionQuestion sq : sessionQuestions) {
            QuestionBank qb = questionBankRepository.findById(sq.getQuestionId()).orElse(null);
            if (qb == null) continue;

            String topicName = null;
            UUID chapterId = null;
            String chapterName = null;
            if (qb.getTopicId() != null) {
                Topic topic = topicRepository.findById(qb.getTopicId()).orElse(null);
                if (topic != null) {
                    topicName = topic.getTopicName();
                    chapterId = topic.getChapterId();
                    if (chapterId != null) {
                        chapterName = chapterRepository.findById(chapterId).map(Chapter::getChapterName).orElse(null);
                    }
                }
            }

            List<StudentQuestionOptionDTO> optionDTOs = Collections.emptyList();
            List<String> acceptedAnswers = null;

            if (qb.getQuestionType() == QuestionType.FILL_BLANK) {
                if (canSeeSolutions) {
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
                                .isCorrect(canSeeSolutions ? opt.getIsCorrect() : null)
                                .build())
                        .collect(Collectors.toList());
            }

            PracticeSessionAnswer ans = answerMap.get(sq.getQuestionId());
            List<UUID> selectedOptionIds = (ans != null) ? new ArrayList<>(parseOptionIds(ans.getSelectedOptionIds())) : Collections.emptyList();
            String answerText = (ans != null) ? ans.getAnswerText() : null;
            Boolean isCorrect = (canSeeSolutions && ans != null) ? ans.getIsCorrect() : null;
            BigDecimal score = (canSeeSolutions && ans != null) ? ans.getScore() : null;
            String explanation = null;

            questionDTOs.add(PracticeSessionQuestionDTO.builder()
                    .questionId(qb.getQuestionId())
                    .content(qb.getContent())
                    .questionType(qb.getQuestionType())
                    .difficultyLevel(qb.getDifficultyLevel())
                    .topicId(qb.getTopicId())
                    .topicName(topicName)
                    .chapterId(chapterId)
                    .chapterName(chapterName)
                    .orderIndex(sq.getOrderIndex())
                    .isReviewed(sq.getIsReviewed())
                    .options(optionDTOs)
                    .acceptedAnswers(acceptedAnswers)
                    .selectedOptionIds(selectedOptionIds)
                    .answerText(answerText)
                    .isCorrect(isCorrect)
                    .score(score)
                    .explanation(explanation)
                    .build());
        }

        return PracticeSessionDTO.builder()
                .sessionId(session.getSessionId())
                .classId(session.getClassId())
                .studentId(session.getStudentId())
                .subjectId(session.getSubjectId())
                .status(session.getStatus())
                .totalQuestions(session.getTotalQuestions())
                .totalScore(session.getTotalScore())
                .accuracyRate(session.getAccuracyRate())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .questions(questionDTOs)
                .build();
    }

    private Set<UUID> parseOptionIds(String str) {
        if (str == null || str.isBlank()) return Collections.emptySet();
        Set<UUID> res = new HashSet<>();
        for (String part : str.split(",")) {
            try {
                res.add(UUID.fromString(part.trim()));
            } catch (Exception ignored) {}
        }
        return res;
    }
}
