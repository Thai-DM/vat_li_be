package com.vatly1.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassSettings;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.*;
import com.vatly1.example.model.request.CreateExamDTO;
import com.vatly1.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class InstructorClassAssignmentAndScopedRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IClassSettingsRepository classSettingsRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IClassEnrollmentRepository classEnrollmentRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ISemesterRepository semesterRepository;

    private String adminToken;
    private String instructorAToken; // gv_nguyen
    private String instructorBToken; // gv_tran
    private String studentToken;     // sv_an

    private User gvNguyen;
    private User gvTran;
    private User svAn;

    private Class classA; // PHY101-01 owned by gv_nguyen
    private Class classB; // PHY101-02 owned by gv_tran

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorAToken = signin("gv_nguyen", "gv_nguyen123456");
        instructorBToken = signin("gv_tran", "gv_tran123456");
        studentToken = signin("sv_an", "sv_an123456");

        gvNguyen = userRepository.findByUsername("gv_nguyen");
        gvTran = userRepository.findByUsername("gv_tran");
        svAn = userRepository.findByUsername("sv_an");

        classA = classRepository.findAll().stream()
                .filter(c -> "PHY101-01".equals(c.getClassCode()))
                .findFirst().orElseThrow();
        classB = classRepository.findAll().stream()
                .filter(c -> "PHY101-02".equals(c.getClassCode()))
                .findFirst().orElseThrow();

        // Ensure classA has gvNguyen as instructor and classB has gvTran
        classA.setInstructorId(gvNguyen.getUserId());
        classRepository.save(classA);

        classB.setInstructorId(gvTran.getUserId());
        classRepository.save(classB);
    }

    private String signin(String username, String password) throws Exception {
        String bodyContent = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String body = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyContent))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("SCENARIO 1: ADMIN phân công Instructor cho Class thành công")
    void test01_adminAssignsInstructorToClass_success() throws Exception {
        String payload = "{\"instructorId\":\"" + gvTran.getUserId() + "\"}";

        mockMvc.perform(put("/api/v1/classes/" + classA.getClassId() + "/instructor")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instructorId").value(gvTran.getUserId().toString()));

        Class updated = classRepository.findById(classA.getClassId()).orElseThrow();
        assertEquals(gvTran.getUserId(), updated.getInstructorId());
    }

    @Test
    @DisplayName("SCENARIO 2: Instructor A thấy Class A được phân công qua GET /api/v1/instructor/classes")
    void test02_instructorA_seesAssignedClassA() throws Exception {
        mockMvc.perform(get("/api/v1/instructor/classes")
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].classId", hasItem(classA.getClassId().toString())));
    }

    @Test
    @DisplayName("SCENARIO 3: Instructor A không thấy Class B trong danh sách lớp phụ trách")
    void test03_instructorA_doesNotSeeClassB() throws Exception {
        mockMvc.perform(get("/api/v1/instructor/classes")
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].classId", not(hasItem(classB.getClassId().toString()))));
    }

    @Test
    @DisplayName("SCENARIO 4: Instructor A không thể xem hoặc update Class B bằng UUID trực tiếp -> 403 Forbidden")
    void test04_instructorA_cannotAccessOrUpdateClassB_directUuid() throws Exception {
        // GET detail
        mockMvc.perform(get("/api/v1/instructor/classes/" + classB.getClassId())
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isForbidden());

        // PUT update
        String updatePayload = "{\"classCode\":\"HACKED_CODE\",\"maxStudents\":100}";
        mockMvc.perform(put("/api/v1/classes/" + classB.getClassId())
                        .header("Authorization", "Bearer " + instructorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 5: Instructor không thể tự phân công hoặc thu hồi phân công nhân sự/giáo viên -> 403 Forbidden")
    void test05_instructorCannotSelfAssignOrRevokeStaffOrInstructor() throws Exception {
        // Instructor cố gán staff
        String staffPayload = "{\"userId\":\"" + gvTran.getUserId() + "\",\"roleInClass\":\"INSTRUCTOR\"}";
        mockMvc.perform(post("/api/v1/classes/" + classA.getClassId() + "/staff")
                        .header("Authorization", "Bearer " + instructorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffPayload))
                .andExpect(status().isForbidden());

        // Instructor cố thu hồi staff
        mockMvc.perform(delete("/api/v1/classes/" + classA.getClassId() + "/staff/" + gvTran.getUserId())
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isForbidden());

        // Instructor cố tự đổi instructor của class
        String assignPayload = "{\"instructorId\":\"" + gvNguyen.getUserId() + "\"}";
        mockMvc.perform(put("/api/v1/classes/" + classB.getClassId() + "/instructor")
                        .header("Authorization", "Bearer " + instructorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload))
                .andExpect(status().isForbidden());

        // Instructor cố thu hồi instructor của class
        mockMvc.perform(delete("/api/v1/classes/" + classA.getClassId() + "/instructor")
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 6: Instructor cập nhật ClassSettings của Class A thành công")
    void test06_instructorUpdatesClassSettings_classA_success() throws Exception {
        String settingsPayload = """
            {
                "practiceEnabled": false,
                "quizEnabled": false,
                "maxPracticeAttempts": 3,
                "practiceShowSolution": false
            }
            """;

        mockMvc.perform(put("/api/v1/classes/" + classA.getClassId() + "/settings")
                        .header("Authorization", "Bearer " + instructorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.practiceEnabled").value(false))
                .andExpect(jsonPath("$.data.quizEnabled").value(false))
                .andExpect(jsonPath("$.data.maxPracticeAttempts").value(3))
                .andExpect(jsonPath("$.data.practiceShowSolution").value(false));

        // Kiểm tra đọc lại qua endpoint Instructor
        mockMvc.perform(get("/api/v1/instructor/classes/" + classA.getClassId() + "/settings")
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.practiceEnabled").value(false))
                .andExpect(jsonPath("$.data.maxPracticeAttempts").value(3));
    }

    @Test
    @DisplayName("SCENARIO 7: Instructor A không thể cập nhật ClassSettings của Class B -> 403 Forbidden")
    void test07_instructorACannotUpdateClassSettingsOfClassB() throws Exception {
        String settingsPayload = "{\"practiceEnabled\": false}";

        mockMvc.perform(put("/api/v1/classes/" + classB.getClassId() + "/settings")
                        .header("Authorization", "Bearer " + instructorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 8: STUDENT không thể sửa ClassSettings -> 403 Forbidden")
    void test08_studentCannotUpdateClassSettings() throws Exception {
        String settingsPayload = "{\"maxPracticeAttempts\": 99}";

        mockMvc.perform(put("/api/v1/classes/" + classA.getClassId() + "/settings")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 9: Cấu hình ClassSettings thực sự ảnh hưởng đến nghiệp vụ (practiceEnabled, maxPracticeAttempts, practiceShowSolution)")
    void test09_classSettingsActivelyEnforceRules() throws Exception {
        // 1. Tạo 1 câu hỏi và 1 đề PRACTICE cho classA
        Topic topic = topicRepository.findAll().stream().findFirst().orElseGet(() -> 
                topicRepository.save(Topic.builder()
                        .subjectId(classA.getSubjectId())
                        .topicName("Topic for Settings Test " + UUID.randomUUID())
                        .orderIndex(1)
                        .build()));
        QuestionBank qb = questionBankRepository.save(QuestionBank.builder()
                .subjectId(classA.getSubjectId())
                .topicId(topic.getTopicId())
                .content("Test Practice Q: 1 + 1 = ?")
                .questionType(QuestionType.FILL_BLANK)
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(gvNguyen.getUserId())
                .build());

        Exam practiceExam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Practice ClassSettings Test")
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .createdBy(gvNguyen.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(com.vatly1.example.entity.ExamQuestion.builder()
                .examId(practiceExam.getExamId())
                .questionId(qb.getQuestionId())
                .orderIndex(1)
                .scoreWeight(java.math.BigDecimal.TEN)
                .build());

        // 2. Khóa luyện tập: practiceEnabled = false
        classSettingsRepository.save(ClassSettings.builder()
                .classId(classA.getClassId())
                .practiceEnabled(false)
                .quizEnabled(true)
                .maxPracticeAttempts(10)
                .practiceShowSolution(true)
                .build());

        // Sinh viên cố bắt đầu thi -> 400 Bad Request
        mockMvc.perform(post("/api/v1/exams/" + practiceExam.getExamId() + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bị khóa")));

        // 3. Mở lại luyện tập nhưng giới hạn maxPracticeAttempts = 1, practiceShowSolution = false
        classSettingsRepository.save(ClassSettings.builder()
                .classId(classA.getClassId())
                .practiceEnabled(true)
                .quizEnabled(true)
                .maxPracticeAttempts(1)
                .practiceShowSolution(false)
                .build());

        // Lượt 1: Thành công
        String startRes = mockMvc.perform(post("/api/v1/exams/" + practiceExam.getExamId() + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String attemptId = objectMapper.readTree(startRes).get("data").get("attemptId").asText();

        // Nộp bài lượt 1
        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Lượt 2: Bị chặn do maxPracticeAttempts = 1
        mockMvc.perform(post("/api/v1/exams/" + practiceExam.getExamId() + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Maximum practice attempts reached")));

        // Lấy chi tiết lượt 1: do practiceShowSolution = false, acceptedAnswers không được trả
        mockMvc.perform(get("/api/v1/exams/attempts/" + attemptId + "/questions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].acceptedAnswers").doesNotExist());
    }

    @Test
    @DisplayName("SCENARIO 10: Instructor chỉ quản lý PRACTICE/QUIZ/MIDTERM đúng lớp phụ trách")
    void test10_instructorOnlyManagesExamsForAssignedClass() throws Exception {
        CreateExamDTO dtoA = CreateExamDTO.builder()
                .classId(classA.getClassId())
                .title("Class A Midterm")
                .examType(ExamType.MIDTERM)
                .durationMinutes(45)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();

        // Tạo cho classA -> 201 Created
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoA)))
                .andExpect(status().isCreated());

        CreateExamDTO dtoB = CreateExamDTO.builder()
                .classId(classB.getClassId())
                .title("Class B Midterm by Intruder")
                .examType(ExamType.MIDTERM)
                .durationMinutes(45)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();

        // Instructor A cố tạo đề cho Class B -> 403 Forbidden
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoB)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 11: Instructor không thể tạo, sửa hoặc xóa FINAL Exam -> 403 Forbidden")
    void test11_instructorCannotManageFinalExam() throws Exception {
        // 1. Cố tạo đề FINAL
        CreateExamDTO finalDto = CreateExamDTO.builder()
                .classId(classA.getClassId())
                .title("Unauthorized Final")
                .examType(ExamType.FINAL)
                .durationMinutes(90)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();

        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(finalDto)))
                .andExpect(status().isForbidden());

        // 2. Tạo đề FINAL bởi Admin
        Exam adminFinal = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Official Final Exam")
                .examType(ExamType.FINAL)
                .durationMinutes(90)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .createdBy(userRepository.findByUsername("admin").getUserId())
                .createdAt(Instant.now())
                .build());

        // 3. Instructor cố sửa đề FINAL -> 403 Forbidden
        String updatePayload = "{\"title\": \"Hacked Final Title\"}";
        mockMvc.perform(put("/api/v1/exams/" + adminFinal.getExamId())
                        .header("Authorization", "Bearer " + instructorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isForbidden());

        // 4. Instructor cố xóa đề FINAL -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/exams/" + adminFinal.getExamId())
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 12: Instructor không thể xem kết quả lượt thi của lớp khác -> 403 Forbidden")
    void test12_instructorCannotViewAttemptsOfOtherClass() throws Exception {
        Exam examB = examRepository.save(Exam.builder()
                .classId(classB.getClassId())
                .title("Class B Quiz")
                .examType(ExamType.QUIZ)
                .durationMinutes(30)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .createdBy(gvTran.getUserId())
                .createdAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/exams/" + examB.getExamId() + "/attempts")
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 13: Thu hồi phân công làm mất quyền truy cập của giáo viên")
    void test13_revokeInstructor_removesAccessImmediately() throws Exception {
        // Admin thu hồi giáo viên của Class A
        mockMvc.perform(delete("/api/v1/classes/" + classA.getClassId() + "/instructor")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Instructor A cố truy cập chi tiết Class A qua endpoint Instructor -> 403 Forbidden
        mockMvc.perform(get("/api/v1/instructor/classes/" + classA.getClassId())
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isForbidden());

        // Instructor A không còn thấy Class A trong danh sách lớp phụ trách
        mockMvc.perform(get("/api/v1/instructor/classes")
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].classId", not(hasItem(classA.getClassId().toString()))));
    }

    @Test
    @DisplayName("SCENARIO 14: Dữ liệu học tập lịch sử không bị xóa hoặc thay đổi khi đổi/thu hồi giáo viên")
    void test14_historicalDataPreservedAfterReassignment() throws Exception {
        // Tạo 1 đề thi và 1 lượt thi trong classA
        Exam exam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Historical Exam")
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .createdBy(gvNguyen.getUserId())
                .createdAt(Instant.now())
                .build());

        // Đổi giáo viên từ gvNguyen sang gvTran
        String assignPayload = "{\"instructorId\":\"" + gvTran.getUserId() + "\"}";
        mockMvc.perform(put("/api/v1/classes/" + classA.getClassId() + "/instructor")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload))
                .andExpect(status().isOk());

        // Kiểm tra đề thi vẫn tồn tại nguyên vẹn
        assertTrue(examRepository.existsById(exam.getExamId()));
        assertEquals(classA.getClassId(), examRepository.findById(exam.getExamId()).orElseThrow().getClassId());

        // Thu hồi giáo viên
        mockMvc.perform(delete("/api/v1/classes/" + classA.getClassId() + "/instructor")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Đề thi vẫn còn nguyên vẹn sau khi thu hồi
        assertTrue(examRepository.existsById(exam.getExamId()));
    }

    @Test
    @DisplayName("SCENARIO 15: Các API Class, Exam và Material hiện có không bị ảnh hưởng (Zero Regression)")
    void test15_existingApisNotBroken() throws Exception {
        // 1. GET /api/v1/classes
        mockMvc.perform(get("/api/v1/classes?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 2. GET /api/v1/exams
        mockMvc.perform(get("/api/v1/exams")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 3. GET /api/v1/classes/{id}/students
        mockMvc.perform(get("/api/v1/classes/" + classA.getClassId() + "/students")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
