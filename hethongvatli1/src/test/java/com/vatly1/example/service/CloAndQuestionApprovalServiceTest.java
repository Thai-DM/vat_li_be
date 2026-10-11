package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.*;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.*;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.CloDTO;
import com.vatly1.example.model.dto.QuestionApprovalDTO;
import com.vatly1.example.model.dto.QuestionBankDTO;
import com.vatly1.example.model.request.CreateCloDTO;
import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class CloAndQuestionApprovalServiceTest {

    @Autowired
    private ICloService cloService;

    @Autowired
    private IQuestionBankService questionBankService;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private ISemesterRepository semesterRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private IQuestionCloRepository questionCloRepository;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private IQuestionApprovalRepository questionApprovalRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private User instructorA;
    private User instructorB;
    private Subject subjectPhys;
    private Subject subjectMath;
    private Topic topicPhys;
    private Topic topicMath;
    private Semester semester;
    private Class physClass;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        adminUser = userRepository.save(User.builder()
                .username("admin_" + uid)
                .email("admin_" + uid + "@test.com")
                .passwordHash(passwordEncoder.encode("Pass@123"))
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        instructorA = userRepository.save(User.builder()
                .username("inst_a_" + uid)
                .email("inst_a_" + uid + "@test.com")
                .passwordHash(passwordEncoder.encode("Pass@123"))
                .role(UserRole.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());

        instructorB = userRepository.save(User.builder()
                .username("inst_b_" + uid)
                .email("inst_b_" + uid + "@test.com")
                .passwordHash(passwordEncoder.encode("Pass@123"))
                .role(UserRole.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());

        subjectPhys = subjectRepository.save(Subject.builder()
                .subjectCode("PHYS_" + uid)
                .subjectName("Vật lý đại cương " + uid)
                .isActive(true)
                .build());

        subjectMath = subjectRepository.save(Subject.builder()
                .subjectCode("MATH_" + uid)
                .subjectName("Toán cao cấp " + uid)
                .isActive(true)
                .build());

        topicPhys = topicRepository.save(Topic.builder()
                .subjectId(subjectPhys.getSubjectId())
                .topicName("Cơ học chất điểm " + uid)
                .orderIndex(1)
                .build());

        topicMath = topicRepository.save(Topic.builder()
                .subjectId(subjectMath.getSubjectId())
                .topicName("Giải tích 1 " + uid)
                .orderIndex(1)
                .build());

        semester = semesterRepository.save(Semester.builder()
                .semesterCode("SEM_" + uid)
                .semesterName("Học kỳ 1 - " + uid)
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusMonths(4))
                .build());

        // Assign instructorA to subjectPhys via a Class
        physClass = classRepository.save(Class.builder()
                .classCode("CLS_PHYS_" + uid)
                .subjectId(subjectPhys.getSubjectId())
                .semesterId(semester.getSemesterId())
                .instructorId(instructorA.getUserId())
                .status(ClassStatus.ACTIVE)
                .build());
    }

    private CreateQuestionDTO buildSampleQuestionDTO(UUID subjectId, UUID topicId, List<UUID> cloIds) {
        return CreateQuestionDTO.builder()
                .subjectId(subjectId)
                .topicId(topicId)
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Nội dung câu hỏi trắc nghiệm kiểm thử")
                .difficultyLevel(DifficultyLevel.EASY)
                .cognitiveLevel("REMEMBER")
                .cloIds(cloIds)
                .options(List.of(
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                                .content("Đáp án A đúng")
                                .isCorrect(true)
                                .orderIndex(1)
                                .build(),
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                                .content("Đáp án B sai")
                                .isCorrect(false)
                                .orderIndex(2)
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("01. Admin tạo CLO hợp lệ thành công")
    void test01_CreateValidClo_Success() {
        CreateCloDTO dto = CreateCloDTO.builder()
                .cloCode("CLO1")
                .description("Vận dụng định luật Newton để giải bài toán cơ học")
                .orderIndex(1)
                .isActive(true)
                .build();

        CloDTO created = cloService.createClo(subjectPhys.getSubjectId(), dto, adminUser.getUserId(), "ADMIN");
        assertNotNull(created.getCloId());
        assertEquals("CLO1", created.getCloCode());
        assertEquals(subjectPhys.getSubjectId(), created.getSubjectId());
        assertTrue(created.getIsActive());
        assertEquals(0L, created.getQuestionCount());
    }

    @Test
    @DisplayName("02. Chặn tạo CLO trùng cloCode trong cùng một Subject (HTTP 409)")
    void test02_PreventDuplicateCloCodeInSameSubject() {
        CreateCloDTO dto1 = CreateCloDTO.builder()
                .cloCode("CLO1")
                .description("CLO ban đầu")
                .orderIndex(1)
                .build();
        cloService.createClo(subjectPhys.getSubjectId(), dto1, adminUser.getUserId(), "ADMIN");

        CreateCloDTO dtoDuplicate = CreateCloDTO.builder()
                .cloCode("CLO1")
                .description("CLO trùng mã")
                .orderIndex(2)
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                cloService.createClo(subjectPhys.getSubjectId(), dtoDuplicate, adminUser.getUserId(), "ADMIN")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    @DisplayName("03. Giảng viên không có quyền tạo hoặc thay đổi CLO (HTTP 403)")
    void test03_InstructorCannotCreateClo() {
        CreateCloDTO dto = CreateCloDTO.builder()
                .cloCode("CLO1")
                .description("CLO do giảng viên cố tạo")
                .orderIndex(1)
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                cloService.createClo(subjectPhys.getSubjectId(), dto, instructorA.getUserId(), "INSTRUCTOR")
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    @DisplayName("04. Tạo câu hỏi mới mặc định ở trạng thái DRAFT và ghi nhận Audit Trail")
    void test04_CreateQuestionDraft_Success() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);

        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");
        assertNotNull(created.getQuestionId());
        assertEquals(ApprovalStatus.DRAFT, created.getApprovalStatus());

        List<QuestionApprovalDTO> history = questionBankService.getQuestionApprovalHistory(
                created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR");
        assertFalse(history.isEmpty());
        assertEquals("CREATE_DRAFT", history.get(0).getAction());
        assertEquals("DRAFT", history.get(0).getToStatus());
    }

    @Test
    @DisplayName("05. Người tạo gửi câu hỏi đi duyệt: DRAFT -> PENDING thành công")
    void test05_DraftToPending_Success() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");

        QuestionBankDTO submitted = questionBankService.submitQuestionForReview(
                created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", "Gửi duyệt lần 1");

        assertEquals(ApprovalStatus.PENDING, submitted.getApprovalStatus());
        assertNull(submitted.getReviewedBy());

        List<QuestionApprovalDTO> history = questionBankService.getQuestionApprovalHistory(
                created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR");
        assertEquals("SUBMIT_FOR_REVIEW", history.get(0).getAction());
        assertEquals("PENDING", history.get(0).getToStatus());
    }

    @Test
    @DisplayName("06. Admin phê duyệt câu hỏi PENDING: PENDING -> APPROVED và lưu thông tin reviewer")
    void test06_PendingToApproved_Success() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");
        questionBankService.submitQuestionForReview(created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", "Gửi duyệt");

        QuestionBankDTO approved = questionBankService.approveQuestion(
                created.getQuestionId(), adminUser.getUserId(), "ADMIN", "Nội dung chuẩn xác");

        assertEquals(ApprovalStatus.APPROVED, approved.getApprovalStatus());
        assertEquals(adminUser.getUserId(), approved.getReviewedBy());
        assertNotNull(approved.getReviewedAt());

        List<QuestionApprovalDTO> history = questionBankService.getQuestionApprovalHistory(
                created.getQuestionId(), adminUser.getUserId(), "ADMIN");
        assertEquals("APPROVE", history.get(0).getAction());
        assertEquals("APPROVED", history.get(0).getToStatus());
    }

    @Test
    @DisplayName("07. Admin từ chối câu hỏi PENDING kèm lý do: PENDING -> REJECTED")
    void test07_PendingToRejected_Success() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");
        questionBankService.submitQuestionForReview(created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", "Gửi duyệt");

        // Reject without reason must fail (HTTP 400)
        CustomException emptyReasonEx = assertThrows(CustomException.class, () ->
                questionBankService.rejectQuestion(created.getQuestionId(), "   ", adminUser.getUserId(), "ADMIN", null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, emptyReasonEx.getHttpStatus());

        // Reject with reason succeeds
        QuestionBankDTO rejected = questionBankService.rejectQuestion(
                created.getQuestionId(), "Đáp án sai công thức", adminUser.getUserId(), "ADMIN", "Cần kiểm tra lại");

        assertEquals(ApprovalStatus.REJECTED, rejected.getApprovalStatus());
        assertEquals("Đáp án sai công thức", rejected.getRejectionReason());
        assertEquals(adminUser.getUserId(), rejected.getReviewedBy());
        assertNotNull(rejected.getReviewedAt());
    }

    @Test
    @DisplayName("08. Câu hỏi bị từ chối được sửa đổi và gửi duyệt lại: REJECTED -> DRAFT/PENDING")
    void test08_RejectedToDraftToPending_Success() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");
        questionBankService.submitQuestionForReview(created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", null);
        questionBankService.rejectQuestion(created.getQuestionId(), "Sai công thức", adminUser.getUserId(), "ADMIN", null);

        // Instructor modifies question
        dto.setContent("Nội dung đã sửa chữa chuẩn xác");
        QuestionBankDTO updated = questionBankService.updateQuestion(
                created.getQuestionId(), dto, instructorA.getUserId(), "INSTRUCTOR");

        assertEquals(ApprovalStatus.DRAFT, updated.getApprovalStatus());
        assertNull(updated.getRejectionReason());

        // Instructor resubmits
        QuestionBankDTO resubmitted = questionBankService.submitQuestionForReview(
                created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", "Đã sửa theo yêu cầu");

        assertEquals(ApprovalStatus.PENDING, resubmitted.getApprovalStatus());
    }

    @Test
    @DisplayName("09. Chặn phê duyệt khi câu hỏi chưa ở trạng thái PENDING (từ DRAFT hoặc REJECTED)")
    void test09_PreventApproveWhenNotPending() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO draft = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");

        // Try approve DRAFT directly
        CustomException exDraft = assertThrows(CustomException.class, () ->
                questionBankService.approveQuestion(draft.getQuestionId(), adminUser.getUserId(), "ADMIN", null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exDraft.getHttpStatus());

        // Submit and reject
        questionBankService.submitQuestionForReview(draft.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", null);
        questionBankService.rejectQuestion(draft.getQuestionId(), "Lý do", adminUser.getUserId(), "ADMIN", null);

        // Try approve REJECTED directly
        CustomException exRejected = assertThrows(CustomException.class, () ->
                questionBankService.approveQuestion(draft.getQuestionId(), adminUser.getUserId(), "ADMIN", null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exRejected.getHttpStatus());
    }

    @Test
    @DisplayName("10. Giảng viên không có quyền phê duyệt hoặc từ chối câu hỏi (HTTP 403)")
    void test10_InstructorCannotApproveOrReject() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");
        questionBankService.submitQuestionForReview(created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", null);

        CustomException exApprove = assertThrows(CustomException.class, () ->
                questionBankService.approveQuestion(created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", null)
        );
        assertEquals(HttpStatus.FORBIDDEN, exApprove.getHttpStatus());

        CustomException exReject = assertThrows(CustomException.class, () ->
                questionBankService.rejectQuestion(created.getQuestionId(), "Từ chối", instructorA.getUserId(), "INSTRUCTOR", null)
        );
        assertEquals(HttpStatus.FORBIDDEN, exReject.getHttpStatus());
    }

    @Test
    @DisplayName("11. Chống IDOR: Giảng viên không thể sửa câu hỏi của giảng viên khác (HTTP 403)")
    void test11_PreventEditingOtherInstructorQuestion() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");

        // Instructor B attempts to update Instructor A's question
        dto.setContent("Hack attempt by instructor B");
        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.updateQuestion(created.getQuestionId(), dto, instructorB.getUserId(), "INSTRUCTOR")
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    @DisplayName("12. Gán CLO cùng môn học cho câu hỏi thành công")
    void test12_LinkQuestionWithCloSameSubject_Success() {
        CloDTO clo = cloService.createClo(subjectPhys.getSubjectId(), CreateCloDTO.builder()
                .cloCode("CLO_PHYS_1")
                .description("Mô tả CLO môn Lý")
                .orderIndex(1)
                .build(), adminUser.getUserId(), "ADMIN");

        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), List.of(clo.getCloId()));
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");

        assertNotNull(created.getCloIds());
        assertEquals(1, created.getCloIds().size());
        assertEquals(clo.getCloId(), created.getCloIds().get(0));

        assertTrue(questionCloRepository.existsByQuestionIdAndCloId(created.getQuestionId(), clo.getCloId()));
    }

    @Test
    @DisplayName("13. Chặn gán CLO khác môn học vào câu hỏi (HTTP 400)")
    void test13_PreventLinkQuestionWithCloDifferentSubject() {
        // Create CLO for Math
        CloDTO mathClo = cloService.createClo(subjectMath.getSubjectId(), CreateCloDTO.builder()
                .cloCode("CLO_MATH_1")
                .description("Mô tả CLO môn Toán")
                .orderIndex(1)
                .build(), adminUser.getUserId(), "ADMIN");

        // Attempt linking Math CLO to Physics question
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), List.of(mathClo.getCloId()));
        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("14. Chặn gán CLO đang ngưng hoạt động (isActive = false) vào câu hỏi (HTTP 400)")
    void test14_PreventLinkQuestionWithInactiveClo() {
        CloDTO inactiveClo = cloService.createClo(subjectPhys.getSubjectId(), CreateCloDTO.builder()
                .cloCode("CLO_INACTIVE")
                .description("CLO cũ không còn áp dụng")
                .orderIndex(2)
                .isActive(false)
                .build(), adminUser.getUserId(), "ADMIN");

        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), List.of(inactiveClo.getCloId()));
        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("15. Câu hỏi đã được đưa vào đề thi bị khóa không thể sửa, xóa hoặc thay đổi CLO (HTTP 400)")
    void test15_QuestionInExamCannotBeDestroyedOrMutated() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");

        // Create an exam and simulate question in exam
        Exam exam = examRepository.save(Exam.builder()
                .classId(physClass.getClassId())
                .title("Đề kiểm tra giữa kỳ")
                .examType(ExamType.QUIZ)
                .durationMinutes(45)
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(created.getQuestionId())
                .orderIndex(1)
                .scoreWeight(new BigDecimal("10.0"))
                .build());

        // Attempt updating question
        dto.setContent("Cố ý thay đổi nội dung khi đã thi");
        CustomException exUpdate = assertThrows(CustomException.class, () ->
                questionBankService.updateQuestion(created.getQuestionId(), dto, instructorA.getUserId(), "INSTRUCTOR")
        );
        assertEquals(HttpStatus.BAD_REQUEST, exUpdate.getHttpStatus());

        // Attempt deleting question
        CustomException exDelete = assertThrows(CustomException.class, () ->
                questionBankService.deleteQuestion(created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR")
        );
        assertEquals(HttpStatus.BAD_REQUEST, exDelete.getHttpStatus());

        // Attempt mutating CLOs
        CustomException exClo = assertThrows(CustomException.class, () ->
                questionBankService.assignClosToQuestion(created.getQuestionId(), List.of(UUID.randomUUID()), instructorA.getUserId(), "INSTRUCTOR")
        );
        assertEquals(HttpStatus.BAD_REQUEST, exClo.getHttpStatus());
    }

    @Test
    @DisplayName("16. Giảng viên chưa được phân công môn học không thể tạo câu hỏi (HTTP 403)")
    void test16_InstructorUnassignedSubjectCannotCreateQuestion() {
        // Instructor B is not assigned to subjectPhys
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.createQuestion(dto, instructorB.getUserId(), "INSTRUCTOR")
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    @DisplayName("17. Concurrency: Hai Admin cùng duyệt/từ chối đồng thời từ trạng thái PENDING -> Chỉ 1 thành công (HTTP 409)")
    void test17_ConcurrentApprovalConflict_ExactlyOneWins() throws Exception {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");
        questionBankService.submitQuestionForReview(created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", null);

        UUID questionId = created.getQuestionId();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> taskApprove = () -> {
            barrier.await();
            try {
                questionBankService.approveQuestion(questionId, adminUser.getUserId(), "ADMIN", "Admin 1 approve");
                successCount.incrementAndGet();
            } catch (CustomException e) {
                if (e.getHttpStatus() == HttpStatus.CONFLICT || e.getHttpStatus() == HttpStatus.BAD_REQUEST) {
                    conflictCount.incrementAndGet();
                }
            }
            return null;
        };

        Callable<Void> taskReject = () -> {
            barrier.await();
            try {
                questionBankService.rejectQuestion(questionId, "Admin 2 reject", adminUser.getUserId(), "ADMIN", null);
                successCount.incrementAndGet();
            } catch (CustomException e) {
                if (e.getHttpStatus() == HttpStatus.CONFLICT || e.getHttpStatus() == HttpStatus.BAD_REQUEST) {
                    conflictCount.incrementAndGet();
                }
            }
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(taskApprove, taskReject));
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Chính xác một thao tác xét duyệt phải thành công");
        assertEquals(1, conflictCount.get(), "Thao tác còn lại phải bị từ chối với trạng thái xung đột");

        QuestionBank finalQuestion = questionBankRepository.findById(questionId).orElseThrow();
        assertTrue(finalQuestion.getApprovalStatus() == ApprovalStatus.APPROVED ||
                finalQuestion.getApprovalStatus() == ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("18. Ghi Audit Trail thất bại -> Rollback toàn bộ trạng thái phê duyệt")
    void test18_RollbackWhenAuditTrailFails() {
        CreateQuestionDTO dto = buildSampleQuestionDTO(subjectPhys.getSubjectId(), topicPhys.getTopicId(), null);
        QuestionBankDTO created = questionBankService.createQuestion(dto, instructorA.getUserId(), "INSTRUCTOR");
        questionBankService.submitQuestionForReview(created.getQuestionId(), instructorA.getUserId(), "INSTRUCTOR", null);

        UUID questionId = created.getQuestionId();

        org.mockito.Mockito.doThrow(new RuntimeException("Simulated audit failure"))
                .when(questionApprovalRepository).save(org.mockito.ArgumentMatchers.argThat(
                        audit -> audit != null && "APPROVE".equals(audit.getAction())
                ));

        try {
            assertThrows(RuntimeException.class, () ->
                    questionBankService.approveQuestion(questionId, adminUser.getUserId(), "ADMIN", "Phê duyệt lỗi audit")
            );

            QuestionBank questionInDb = questionBankRepository.findById(questionId).orElseThrow();
            assertEquals(ApprovalStatus.PENDING, questionInDb.getApprovalStatus(),
                    "Trạng thái câu hỏi phải giữ nguyên là PENDING khi Audit Trail bị rollback");
            assertNull(questionInDb.getReviewedBy(), "reviewedBy không được ghi nhận khi rollback");
        } finally {
            org.mockito.Mockito.reset(questionApprovalRepository);
        }
    }
}
