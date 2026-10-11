package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.MatrixValidationItemDTO;
import com.vatly1.example.model.request.CreateExamDTO;
import com.vatly1.example.model.response.MatrixValidationResultDTO;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IExamMatrixDetailRepository;
import com.vatly1.example.repository.IExamMatrixRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class ExamAutoGenerationApprovalTest {

    @Autowired
    private IExamService examService;

    @Autowired
    private IExamMatrixService examMatrixService;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamMatrixRepository examMatrixRepository;

    @Autowired
    private IExamMatrixDetailRepository examMatrixDetailRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    private User instructor;
    private Class targetClass;
    private Topic testTopic;

    @BeforeEach
    void setUp() {
        instructor = userRepository.findByUsername("gv_nguyen");
        assertNotNull(instructor, "Instructor gv_nguyen must exist");

        targetClass = classRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getInstructorId(), instructor.getUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Class for gv_nguyen not found"));

        testTopic = topicRepository.save(Topic.builder()
                .subjectId(targetClass.getSubjectId())
                .topicName("Topic Kiểm Thử Ma Trận " + UUID.randomUUID())
                .orderIndex(99)
                .build());
    }

    private QuestionBank createQuestion(Topic topic, DifficultyLevel difficulty, ApprovalStatus status, String content) {
        return questionBankRepository.save(QuestionBank.builder()
                .subjectId(topic.getSubjectId())
                .topicId(topic.getTopicId())
                .difficultyLevel(difficulty)
                .questionType(QuestionType.MCQ_SINGLE)
                .approvalStatus(status)
                .content(content)
                .createdBy(instructor.getUserId())
                .build());
    }

    private ExamMatrix createMatrix(Topic topic, DifficultyLevel difficulty, int numQuestions) {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(topic.getSubjectId())
                .matrixName("Ma trận test - " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .build());

        examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(topic.getSubjectId())
                .topicId(topic.getTopicId())
                .difficultyLevel(difficulty)
                .numQuestions(numQuestions)
                .weightPercent(new BigDecimal("1.0"))
                .build());

        return matrix;
    }

    private ExamMatrix createMatrixWithDetails(Topic topic, List<ExamMatrixDetail> details) {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(topic.getSubjectId())
                .matrixName("Ma trận đa chi tiết test - " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .build());

        for (ExamMatrixDetail d : details) {
            d.setMatrixId(matrix.getMatrixId());
            d.setSubjectId(topic.getSubjectId());
            if (d.getWeightPercent() == null) {
                d.setWeightPercent(BigDecimal.ONE);
            }
            examMatrixDetailRepository.save(d);
        }

        return matrix;
    }

    private Exam createExamForMatrix(ExamMatrix matrix) {
        return examRepository.save(Exam.builder()
                .classId(targetClass.getClassId())
                .matrixId(matrix.getMatrixId())
                .title("Bài thi test sinh tự động")
                .examType(ExamType.PRACTICE)
                .durationMinutes(45)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .createdBy(instructor.getUserId())
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Test 1: Chỉ có câu hỏi APPROVED -> Sinh đề thành công, tất cả câu hỏi được chọn đều là APPROVED")
    void testAutoGenerate_OnlyApprovedQuestions() {
        QuestionBank q1 = createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 1 Easy Approved");
        QuestionBank q2 = createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 2 Easy Approved");

        ExamMatrix matrix = createMatrix(testTopic, DifficultyLevel.EASY, 2);
        Exam exam = createExamForMatrix(matrix);

        int added = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(2, added, "Phải sinh đủ 2 câu hỏi theo ma trận");

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(2, examQuestions.size());

        Set<UUID> questionIds = examQuestions.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        assertTrue(questionIds.contains(q1.getQuestionId()));
        assertTrue(questionIds.contains(q2.getQuestionId()));

        for (ExamQuestion eq : examQuestions) {
            QuestionBank q = questionBankRepository.findById(eq.getQuestionId()).orElseThrow();
            assertEquals(ApprovalStatus.APPROVED, q.getApprovalStatus(), "Tất cả câu hỏi trong đề phải có trạng thái APPROVED");
        }
    }

    @Test
    @DisplayName("Test 2: Có lẫn APPROVED, PENDING, DRAFT, REJECTED -> Chỉ chọn APPROVED, loại bỏ hoàn toàn chưa duyệt")
    void testAutoGenerate_MixedApprovalStatuses_OnlyApprovedSelected() {
        QuestionBank app1 = createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 1 Easy Approved");
        QuestionBank app2 = createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 2 Easy Approved");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.PENDING, "Câu Pending Easy");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.DRAFT, "Câu Draft Easy");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.REJECTED, "Câu Rejected Easy");

        ExamMatrix matrix = createMatrix(testTopic, DifficultyLevel.EASY, 2);
        Exam exam = createExamForMatrix(matrix);

        int added = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(2, added);

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(2, examQuestions.size());

        for (ExamQuestion eq : examQuestions) {
            QuestionBank q = questionBankRepository.findById(eq.getQuestionId()).orElseThrow();
            assertEquals(ApprovalStatus.APPROVED, q.getApprovalStatus(), "Tuyệt đối không được chọn câu hỏi chưa APPROVED");
            assertNotEquals(ApprovalStatus.PENDING, q.getApprovalStatus());
            assertNotEquals(ApprovalStatus.DRAFT, q.getApprovalStatus());
            assertNotEquals(ApprovalStatus.REJECTED, q.getApprovalStatus());
        }
    }

    @Test
    @DisplayName("Test 3: Truy vấn chính không có kết quả, dùng Fallback cùng Topic -> Fallback chỉ chọn câu hỏi APPROVED")
    void testAutoGenerate_PrimaryQueryEmpty_FallbackOnlyPicksApproved() {
        // Ma trận yêu cầu 2 câu HARD
        // Cấp độ HARD chỉ có câu hỏi PENDING hoặc DRAFT (không có HARD APPROVED nào)
        createQuestion(testTopic, DifficultyLevel.HARD, ApprovalStatus.PENDING, "Câu Hard Chưa Duyệt 1");
        createQuestion(testTopic, DifficultyLevel.HARD, ApprovalStatus.DRAFT, "Câu Hard Bản Nháp 2");

        // Các cấp độ khác (EASY, MEDIUM) trong cùng Topic có 2 câu APPROVED và 1 câu REJECTED
        QuestionBank appEasy = createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu Easy Approved");
        QuestionBank appMed = createQuestion(testTopic, DifficultyLevel.MEDIUM, ApprovalStatus.APPROVED, "Câu Medium Approved");
        createQuestion(testTopic, DifficultyLevel.MEDIUM, ApprovalStatus.REJECTED, "Câu Medium Rejected");

        ExamMatrix matrix = createMatrix(testTopic, DifficultyLevel.HARD, 2);
        Exam exam = createExamForMatrix(matrix);

        int added = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(2, added, "Fallback phải lấy đủ 2 câu APPROVED khác trong cùng Topic");

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(2, examQuestions.size());

        Set<UUID> chosenIds = examQuestions.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        assertTrue(chosenIds.contains(appEasy.getQuestionId()), "Phải lấy câu Easy Approved từ fallback");
        assertTrue(chosenIds.contains(appMed.getQuestionId()), "Phải lấy câu Medium Approved từ fallback");

        for (ExamQuestion eq : examQuestions) {
            QuestionBank q = questionBankRepository.findById(eq.getQuestionId()).orElseThrow();
            assertEquals(ApprovalStatus.APPROVED, q.getApprovalStatus(), "Fallback chỉ được phép chọn câu APPROVED");
        }
    }

    @Test
    @DisplayName("Test 4: Không đủ câu hỏi APPROVED -> Ném lỗi 400 Bad Request, Rollback không tạo dữ liệu dở dang")
    void testAutoGenerate_InsufficientApprovedQuestions_ThrowsBadRequestAndRollsBack() {
        // Ma trận yêu cầu 3 câu EASY
        // Ngân hàng chỉ có 1 câu APPROVED EASY, còn lại là PENDING và DRAFT
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu Easy Approved Duy Nhất");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.PENDING, "Câu Easy Pending 1");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.PENDING, "Câu Easy Pending 2");
        createQuestion(testTopic, DifficultyLevel.MEDIUM, ApprovalStatus.DRAFT, "Câu Medium Draft");

        ExamMatrix matrix = createMatrix(testTopic, DifficultyLevel.EASY, 3);
        Exam exam = createExamForMatrix(matrix);

        CustomException exception = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        }, "Phải ném CustomException khi không đủ câu hỏi APPROVED");

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Không đủ câu hỏi đã được phê duyệt (APPROVED)"));

        // Xác nhận dữ liệu không bị lưu dở dang (rollback)
        List<ExamQuestion> savedQuestions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(0, savedQuestions.size(), "Dữ liệu câu hỏi phải được rollback hoàn toàn, không lưu dở dang");
    }

    @Test
    @DisplayName("Test 5: Không có câu hỏi APPROVED nào -> Ném lỗi 400 Bad Request, Rollback an toàn")
    void testAutoGenerate_ZeroApprovedQuestions_ThrowsBadRequest() {
        // Ma trận yêu cầu 2 câu EASY
        // Ngân hàng chỉ có PENDING, DRAFT, REJECTED (0 câu APPROVED)
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.PENDING, "Câu 1 Pending");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.DRAFT, "Câu 2 Draft");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.REJECTED, "Câu 3 Rejected");

        ExamMatrix matrix = createMatrix(testTopic, DifficultyLevel.EASY, 2);
        Exam exam = createExamForMatrix(matrix);

        CustomException exception = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Không đủ câu hỏi đã được phê duyệt (APPROVED)"));

        List<ExamQuestion> savedQuestions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(0, savedQuestions.size(), "Không có câu hỏi nào được lưu vào đề thi");
    }

    @Test
    @DisplayName("Test 6: Kiểm tra tạo đề trực tiếp kèm ma trận (createExam) khi thiếu câu hỏi APPROVED -> Rollback cả đề thi")
    void testCreateExamWithMatrix_InsufficientApprovedQuestions_RollsBackExam() {
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 1 Approved");
        // Ma trận yêu cầu 2 câu nhưng chỉ có 1 câu approved
        ExamMatrix matrix = createMatrix(testTopic, DifficultyLevel.EASY, 2);

        CreateExamDTO dto = CreateExamDTO.builder()
                .classId(targetClass.getClassId())
                .matrixId(matrix.getMatrixId())
                .title("Đề thi thử nghiệm Rollback")
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .build();

        long initialExamCount = examRepository.count();

        CustomException exception = assertThrows(CustomException.class, () -> {
            examService.createExam(dto, instructor.getUserId());
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());

        // Kiểm tra đề thi không được tạo rác trong DB
        long afterExamCount = examRepository.count();
        assertEquals(initialExamCount, afterExamCount, "Đề thi không hoàn chỉnh phải được rollback hoàn toàn, không tạo rác");
    }

    @Test
    @DisplayName("Test 7 (Tính nhất quán): Đủ câu APPROVED đúng độ khó -> Validation báo hợp lệ và sinh đề thành công")
    void testConsistency_SufficientApprovedExact() {
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 1 Easy");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 2 Easy");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 3 Easy");

        ExamMatrix matrix = createMatrix(testTopic, DifficultyLevel.EASY, 3);
        Exam exam = createExamForMatrix(matrix);

        // 1. Kiểm tra validation
        MatrixValidationResultDTO validation = examMatrixService.validateExamMatrix(matrix.getMatrixId());
        assertTrue(validation.isValid(), "Validation phải báo ma trận hợp lệ");
        assertEquals(3, validation.getTotalRequired());
        assertEquals(3, validation.getTotalAvailable());
        assertEquals(1, validation.getItems().size());
        MatrixValidationItemDTO item = validation.getItems().get(0);
        assertTrue(item.isSufficient());
        assertTrue(item.getIsExactSufficient());
        assertEquals(3, item.getExactAvailable());
        assertEquals(0, item.getFallbackAvailable());

        // 2. Kiểm tra sinh đề
        int added = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(3, added, "Phải sinh đủ 3 câu đúng như validation báo");
        assertEquals(3, examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId()).size());
    }

    @Test
    @DisplayName("Test 8 (Tính nhất quán): Có câu chưa duyệt và thiếu câu APPROVED -> Validation báo không đủ và Sinh đề ném lỗi 400")
    void testConsistency_UnapprovedIgnored_BothFailConsistently() {
        // Chỉ có 1 câu APPROVED, nhưng có 5 câu DRAFT/PENDING
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 1 Approved");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.DRAFT, "Câu Draft 1");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.DRAFT, "Câu Draft 2");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.PENDING, "Câu Pending 1");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.PENDING, "Câu Pending 2");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.REJECTED, "Câu Rejected 1");

        ExamMatrix matrix = createMatrix(testTopic, DifficultyLevel.EASY, 3);
        Exam exam = createExamForMatrix(matrix);

        // 1. Kiểm tra validation: Phải loại bỏ câu chưa duyệt và báo không đủ
        MatrixValidationResultDTO validation = examMatrixService.validateExamMatrix(matrix.getMatrixId());
        assertFalse(validation.isValid(), "Validation phải báo KHÔNG hợp lệ vì chỉ có 1 câu APPROVED");
        assertEquals(3, validation.getTotalRequired());
        assertEquals(1, validation.getTotalAvailable());
        assertFalse(validation.getItems().get(0).isSufficient());
        assertEquals(1, validation.getItems().get(0).getAvailableQuestions());
        assertTrue(validation.getWarnings().stream().anyMatch(w -> w.contains("chưa được phê duyệt")),
                "Validation phải cảnh báo các câu hỏi chưa được phê duyệt");

        // 2. Kiểm tra sinh đề: Phải ném CustomException (400)
        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals(0, examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId()).size(),
                "Dữ liệu đề thi phải rỗng, không lưu câu hỏi dở dang");
    }

    @Test
    @DisplayName("Test 9 (Tính nhất quán & Minh bạch Fallback): Dùng Fallback độ khó -> Validation cảnh báo số câu bù và Sinh đề thành công")
    void testConsistency_FallbackWithExplicitWarning() {
        // Yêu cầu: 3 câu HARD. Ngân hàng chỉ có 1 câu HARD APPROVED, nhưng có 2 câu EASY APPROVED
        createQuestion(testTopic, DifficultyLevel.HARD, ApprovalStatus.APPROVED, "Câu 1 Hard Approved");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 1 Easy Approved");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Câu 2 Easy Approved");

        ExamMatrix matrix = createMatrix(testTopic, DifficultyLevel.HARD, 3);
        Exam exam = createExamForMatrix(matrix);

        // 1. Kiểm tra validation: Phải báo isValid=true (do bù được qua fallback), nhưng isExactSufficient=false và có warning chi tiết
        MatrixValidationResultDTO validation = examMatrixService.validateExamMatrix(matrix.getMatrixId());
        assertTrue(validation.isValid(), "Validation phải hợp lệ vì đủ câu sau khi bù fallback");
        MatrixValidationItemDTO item = validation.getItems().get(0);
        assertTrue(item.isSufficient());
        assertFalse(item.getIsExactSufficient(), "Không đủ 100% câu đúng độ khó");
        assertEquals(1, item.getExactAvailable(), "Có 1 câu đúng độ khó");
        assertEquals(2, item.getFallbackAvailable(), "Bù 2 câu từ độ khó khác");
        assertEquals(2, item.getFallbackNeeded(), "Cần bù 2 câu");

        assertTrue(validation.getWarnings().stream().anyMatch(w -> w.contains("Cần bù 2 câu từ độ khó khác")),
                "Validation phải thông báo rõ ràng về việc bù độ khó");

        // 2. Kiểm tra sinh đề: Phải sinh đủ 3 câu (1 Hard + 2 Easy)
        int added = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(3, added);

        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(3, questions.size());
        for (ExamQuestion eq : questions) {
            QuestionBank q = questionBankRepository.findById(eq.getQuestionId()).orElseThrow();
            assertEquals(ApprovalStatus.APPROVED, q.getApprovalStatus());
        }
    }

    @Test
    @DisplayName("Test 10 (Giao nhau & Chống trùng lặp): Nhiều dòng ma trận chung Topic nhưng tổng câu thiếu -> Cả 2 đều báo không đủ")
    void testConsistency_MultipleDetailsIntersectingTopic_InsufficientAcrossDetails() {
        // Topic có tổng cộng: 2 EASY APPROVED, 2 HARD APPROVED (Tổng 4 câu APPROVED)
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Easy 1");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Easy 2");
        createQuestion(testTopic, DifficultyLevel.HARD, ApprovalStatus.APPROVED, "Hard 1");
        createQuestion(testTopic, DifficultyLevel.HARD, ApprovalStatus.APPROVED, "Hard 2");

        // Ma trận yêu cầu:
        // Detail 1: 3 câu EASY (cần 2 Easy + 1 Hard fallback)
        // Detail 2: 2 câu HARD (chỉ còn 1 Hard khả dụng vì 1 Hard đã bị Detail 1 lấy!)
        // Tổng yêu cầu = 5 câu, khả dụng chỉ có 4 câu
        ExamMatrix matrix = createMatrixWithDetails(testTopic, List.of(
                ExamMatrixDetail.builder().topicId(testTopic.getTopicId()).difficultyLevel(DifficultyLevel.EASY).numQuestions(3).build(),
                ExamMatrixDetail.builder().topicId(testTopic.getTopicId()).difficultyLevel(DifficultyLevel.HARD).numQuestions(2).build()
        ));
        Exam exam = createExamForMatrix(matrix);

        // 1. Kiểm tra validation: Phải phát hiện Detail 2 bị thiếu câu do Detail 1 đã claim!
        MatrixValidationResultDTO validation = examMatrixService.validateExamMatrix(matrix.getMatrixId());
        assertFalse(validation.isValid(), "Validation phải báo KHÔNG hợp lệ do thiếu câu khi giao nhau giữa các dòng");
        assertEquals(5, validation.getTotalRequired());
        assertEquals(4, validation.getTotalAvailable()); // Chỉ có 4 câu tổng cộng

        MatrixValidationItemDTO itemDetail1 = validation.getItems().get(0);
        MatrixValidationItemDTO itemDetail2 = validation.getItems().get(1);
        assertTrue(itemDetail1.isSufficient(), "Detail 1 đủ 3 câu (2 exact + 1 fallback)");
        assertFalse(itemDetail2.isSufficient(), "Detail 2 không đủ câu vì câu Hard thứ 2 đã bị Detail 1 claim");

        // 2. Kiểm tra sinh đề: Phải ném lỗi 400 Bad Request và rollback toàn bộ
        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals(0, examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId()).size(),
                "Rollback hoàn toàn: Không lưu câu hỏi dở dang từ Detail 1");
    }

    @Test
    @DisplayName("Test 11 (Giao nhau & Chống trùng lặp): Nhiều dòng ma trận chung Topic đủ câu -> Không trùng lặp câu hỏi")
    void testConsistency_MultipleDetailsIntersectingTopic_SufficientAndNoDuplicates() {
        // Topic có: 3 EASY APPROVED, 2 HARD APPROVED (Tổng 5 câu APPROVED)
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Easy 1");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Easy 2");
        createQuestion(testTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED, "Easy 3");
        createQuestion(testTopic, DifficultyLevel.HARD, ApprovalStatus.APPROVED, "Hard 1");
        createQuestion(testTopic, DifficultyLevel.HARD, ApprovalStatus.APPROVED, "Hard 2");

        // Detail 1: 2 EASY
        // Detail 2: 2 HARD
        ExamMatrix matrix = createMatrixWithDetails(testTopic, List.of(
                ExamMatrixDetail.builder().topicId(testTopic.getTopicId()).difficultyLevel(DifficultyLevel.EASY).numQuestions(2).build(),
                ExamMatrixDetail.builder().topicId(testTopic.getTopicId()).difficultyLevel(DifficultyLevel.HARD).numQuestions(2).build()
        ));
        Exam exam = createExamForMatrix(matrix);

        // 1. Validation
        MatrixValidationResultDTO validation = examMatrixService.validateExamMatrix(matrix.getMatrixId());
        assertTrue(validation.isValid(), "Validation phải báo hợp lệ");
        assertEquals(4, validation.getTotalRequired());
        assertEquals(4, validation.getTotalAvailable());

        // 2. Generation
        int added = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(4, added);

        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(4, questions.size());

        Set<UUID> chosenIds = questions.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        assertEquals(4, chosenIds.size(), "Tuyệt đối không có câu hỏi nào bị chọn trùng lặp");
    }
}
