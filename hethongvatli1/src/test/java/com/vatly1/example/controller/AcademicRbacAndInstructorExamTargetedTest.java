package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.QuestionAcceptedAnswer;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionOption;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionAcceptedAnswerRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AcademicRbacAndInstructorExamTargetedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private QuestionAcceptedAnswerRepository questionAcceptedAnswerRepository;

    private String adminToken;
    private String instructorToken; // gv_nguyen (Class 1)
    private String instructorBToken; // gv_tran (Class 2)
    private String studentToken; // sv_an

    private UUID subjectId;
    private UUID class1Id;
    private UUID class2Id;
    private UUID testChapterId;
    private UUID testTopicId;
    private UUID testCloId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        instructorBToken = signin("gv_tran", "gv_tran123456");
        studentToken = signin("sv_an", "sv_an123456");

        User gvNguyen = userRepository.findByUsername("gv_nguyen");
        User gvTran = userRepository.findByUsername("gv_tran");

        List<Class> classes = classRepository.findAll();
        Class class1 = classes.stream()
                .filter(c -> Objects.equals(c.getInstructorId(), gvNguyen.getUserId()))
                .findFirst()
                .orElse(classes.get(0));
        class1Id = class1.getClassId();
        subjectId = class1.getSubjectId();

        Class class2 = classes.stream()
                .filter(c -> Objects.equals(c.getInstructorId(), gvTran.getUserId()))
                .findFirst()
                .orElse(classes.size() > 1 ? classes.get(1) : classes.get(0));
        class2Id = class2.getClassId();

        // Admin ensures a baseline Chapter exists
        String chapCode = "CH_BASE_" + System.currentTimeMillis();
        String chapRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "subjectId": "%s",
                            "chapterCode": "%s",
                            "chapterName": "Chương Baseline"
                        }
                        """.formatted(subjectId, chapCode)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        testChapterId = UUID.fromString(objectMapper.readTree(chapRes).get("data").get("chapterId").asText());

        // Admin ensures a baseline Topic exists
        String topicRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "subjectId": "%s",
                            "chapterId": "%s",
                            "topicName": "Topic Baseline %s"
                        }
                        """.formatted(subjectId, testChapterId, System.currentTimeMillis())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        testTopicId = UUID.fromString(objectMapper.readTree(topicRes).get("data").get("topicId").asText());

        // Admin ensures a baseline CLO exists
        String cloRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "cloCode": "CLO_BASE_%s",
                            "description": "Chuẩn đầu ra Baseline",
                            "orderIndex": 1
                        }
                        """.formatted(System.currentTimeMillis())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        testCloId = UUID.fromString(objectMapper.readTree(cloRes).get("data").get("cloId").asText());
    }

    private String signin(String username, String password) throws Exception {
        String res = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("data").get("accessToken").asText();
    }

    // =========================================================================
    // KỊCH BẢN 1: ADMIN tạo Chapter thành công
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 1: ADMIN tạo Chapter thành công")
    void test01_AdminCreatesChapterSuccess() throws Exception {
        String code = "CH_ADM_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "subjectId": "%s",
                            "chapterCode": "%s",
                            "chapterName": "Chương Admin Tạo"
                        }
                        """.formatted(subjectId, code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.chapterCode").value(code));
    }

    // =========================================================================
    // KỊCH BẢN 2: INSTRUCTOR tạo Chapter bị từ chối 403
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 2: INSTRUCTOR tạo Chapter bị từ chối 403")
    void test02_InstructorCreatesChapterForbidden() throws Exception {
        String code = "CH_INS_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "subjectId": "%s",
                            "chapterCode": "%s",
                            "chapterName": "Chương Giảng Viên Thử Tạo"
                        }
                        """.formatted(subjectId, code)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // KỊCH BẢN 3: INSTRUCTOR sửa hoặc xóa Chapter bị từ chối
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 3: INSTRUCTOR sửa hoặc xóa Chapter bị từ chối")
    void test03_InstructorUpdateOrDeleteChapterForbidden() throws Exception {
        // PUT update chapter
        mockMvc.perform(put("/api/v1/chapters/" + testChapterId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"chapterName": "Chương Đã Sửa Lậu"}
                        """))
                .andExpect(status().isForbidden());

        // PUT status chapter
        mockMvc.perform(put("/api/v1/chapters/" + testChapterId + "/status?isActive=false")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());

        // PUT reorder topics in chapter
        mockMvc.perform(put("/api/v1/chapters/" + testChapterId + "/topics/reorder")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"topicIds": ["%s"]}
                        """.formatted(testTopicId)))
                .andExpect(status().isForbidden());

        // DELETE chapter
        mockMvc.perform(delete("/api/v1/chapters/" + testChapterId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // KỊCH BẢN 4: INSTRUCTOR không thể thay đổi Subject/Topic/CLO
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 4: INSTRUCTOR không thể thay đổi Subject/Topic/CLO")
    void test04_InstructorCannotMutateSubjectTopicClo() throws Exception {
        // 1. Mutate Subject
        mockMvc.perform(post("/api/v1/subjects")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"subjectCode": "PHY_HACK", "subjectName": "Hacked Subject"}
                        """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/subjects/" + subjectId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"subjectName": "Subject Renamed"}
                        """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/subjects/" + subjectId + "/toggle-status")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());

        // 2. Mutate Topic
        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"subjectId": "%s", "topicName": "Topic Hack"}
                        """.formatted(subjectId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/topics/" + testTopicId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"subjectId": "%s", "topicName": "Topic Hack Update"}
                        """.formatted(subjectId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/topics/" + testTopicId + "/assign-chapter?chapterId=" + testChapterId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/topics/" + testTopicId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());

        // 3. Mutate CLO
        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"cloCode": "CLO_HACK", "description": "Desc", "orderIndex": 1}
                        """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/clos/" + testCloId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"cloCode": "CLO_UPD", "description": "Desc Upd", "orderIndex": 1}
                        """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/clos/" + testCloId + "/toggle-status?isActive=false")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/clos/" + testCloId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // KỊCH BẢN 5: INSTRUCTOR vẫn GET được cấu trúc môn học theo quyền phù hợp
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 5: INSTRUCTOR vẫn GET được cấu trúc môn học theo quyền phù hợp")
    void test05_InstructorCanGetAcademicStructure() throws Exception {
        mockMvc.perform(get("/api/v1/subjects")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/subjects/" + subjectId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/chapters/" + testChapterId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/topics")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================================
    // KỊCH BẢN 6: INSTRUCTOR vẫn tạo QuestionBank được theo workflow hiện tại
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 6: INSTRUCTOR vẫn tạo QuestionBank được theo workflow hiện tại")
    void test06_InstructorCanCreateQuestionBank() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "subjectId": "%s",
                            "topicId": "%s",
                            "questionType": "MCQ_SINGLE",
                            "content": "Câu hỏi test quyền giảng viên tạo ngân hàng câu hỏi",
                            "difficultyLevel": "EASY",
                            "options": [
                                {"optionText": "Phương án A", "isCorrect": true},
                                {"optionText": "Phương án B", "isCorrect": false}
                            ]
                        }
                        """.formatted(subjectId, testTopicId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.questionId").isNotEmpty());
    }

    // =========================================================================
    // KỊCH BẢN 7: INSTRUCTOR tạo PRACTICE trong lớp mình thành công
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 7: INSTRUCTOR tạo PRACTICE trong lớp mình thành công")
    void test07_InstructorCreatesPracticeExamSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Bài Luyện Tập Tuần 1",
                            "examType": "PRACTICE",
                            "durationMinutes": 30
                        }
                        """.formatted(class1Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.examType").value("PRACTICE"))
                .andExpect(jsonPath("$.data.title").value("Bài Luyện Tập Tuần 1"));
    }

    // =========================================================================
    // KỊCH BẢN 8: INSTRUCTOR tạo QUIZ/MIDTERM theo quyền được giao
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 8: INSTRUCTOR tạo QUIZ/MIDTERM theo quyền được giao")
    void test08_InstructorCreatesQuizAndMidtermSuccess() throws Exception {
        // 1. Tạo QUIZ
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Bài Quiz 15 Phút",
                            "examType": "QUIZ",
                            "durationMinutes": 15
                        }
                        """.formatted(class1Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.examType").value("QUIZ"));

        // 2. Tạo MIDTERM
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Thi Giữa Kỳ",
                            "examType": "MIDTERM",
                            "durationMinutes": 60
                        }
                        """.formatted(class1Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.examType").value("MIDTERM"));
    }

    // =========================================================================
    // KỊCH BẢN 9: INSTRUCTOR chỉnh cấu hình bài thi chưa có Attempt
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 9: INSTRUCTOR chỉnh cấu hình bài thi chưa có Attempt")
    void test09_InstructorUpdatesExamWithoutAttemptsSuccess() throws Exception {
        // 1. Tạo Exam
        String examRes = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Exam Cần Chỉnh Cấu Hình",
                            "examType": "QUIZ",
                            "durationMinutes": 20
                        }
                        """.formatted(class1Id)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String examId = objectMapper.readTree(examRes).get("data").get("examId").asText();

        // 2. Cập nhật cấu hình
        mockMvc.perform(put("/api/v1/exams/" + examId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "title": "Exam Đã Chỉnh Sửa Tên",
                            "durationMinutes": 25
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Exam Đã Chỉnh Sửa Tên"))
                .andExpect(jsonPath("$.data.durationMinutes").value(25));

        // 3. Thêm câu hỏi APPROVED vào Exam
        QuestionBank q = QuestionBank.builder()
                .subjectId(subjectId)
                .topicId(testTopicId)
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Câu hỏi trắc nghiệm hợp lệ")
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        QuestionBank savedQ = questionBankRepository.save(q);

        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "questionId": "%s",
                            "scoreWeight": 1.0,
                            "orderIndex": 1
                        }
                        """.formatted(savedQ.getQuestionId())))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // KỊCH BẢN 10: INSTRUCTOR không sửa Exam lớp khác
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 10: INSTRUCTOR không sửa Exam lớp khác")
    void test10_InstructorCannotModifyOtherClassExam() throws Exception {
        // Tạo Exam ở Class 2 (của gv_tran)
        String examRes = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Exam Của Lớp 2",
                            "examType": "QUIZ",
                            "durationMinutes": 30
                        }
                        """.formatted(class2Id)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String exam2Id = objectMapper.readTree(examRes).get("data").get("examId").asText();

        // gv_nguyen cố sửa Exam của Class 2 -> 403 Forbidden
        mockMvc.perform(put("/api/v1/exams/" + exam2Id)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"title": "Hacked Title"}
                        """))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // KỊCH BẢN 11: INSTRUCTOR không sửa FINAL
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 11: INSTRUCTOR không sửa FINAL")
    void test11_InstructorCannotCreateOrModifyFinalExam() throws Exception {
        // 1. INSTRUCTOR cố tạo FINAL -> 403 Forbidden
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Final Exam Lậu",
                            "examType": "FINAL",
                            "durationMinutes": 90
                        }
                        """.formatted(class1Id)))
                .andExpect(status().isForbidden());

        // 2. ADMIN tạo FINAL hợp lệ
        String finalRes = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Kỳ Thi Cuối Kỳ Chính Thức",
                            "examType": "FINAL",
                            "durationMinutes": 90
                        }
                        """.formatted(class1Id)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String finalExamId = objectMapper.readTree(finalRes).get("data").get("examId").asText();

        // 3. INSTRUCTOR cố sửa FINAL -> 403 Forbidden
        mockMvc.perform(put("/api/v1/exams/" + finalExamId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"title": "Giảng Viên Sửa Điểm/Tên Final"}
                        """))
                .andExpect(status().isForbidden());

        // 4. INSTRUCTOR cố xóa FINAL -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/exams/" + finalExamId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // KỊCH BẢN 12: INSTRUCTOR không sửa dữ liệu Exam đã có Attempt nếu làm ảnh hưởng lịch sử
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 12: INSTRUCTOR không sửa dữ liệu Exam đã có Attempt nếu làm ảnh hưởng lịch sử")
    void test12_InstructorCannotModifyExamWithAttempts() throws Exception {
        // 1. Tạo Practice Exam có câu hỏi
        QuestionBank q = QuestionBank.builder()
                .subjectId(subjectId)
                .topicId(testTopicId)
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Câu hỏi kiểm tra khóa bài thi đã có attempt")
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        QuestionBank savedQ = questionBankRepository.save(q);

        String examRes = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Exam Có Sinh Viên Làm Bài",
                            "examType": "PRACTICE",
                            "durationMinutes": 30
                        }
                        """.formatted(class1Id)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String examId = objectMapper.readTree(examRes).get("data").get("examId").asText();

        // Thêm câu hỏi
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "questionId": "%s",
                            "scoreWeight": 1.0,
                            "orderIndex": 1
                        }
                        """.formatted(savedQ.getQuestionId())))
                .andExpect(status().isOk());

        // 2. Sinh viên sv_an tạo attempt
        mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());

        // 3. Giảng viên cố cập nhật cấu hình -> 400 Bad Request
        mockMvc.perform(put("/api/v1/exams/" + examId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"title": "Cố Sửa Khi Đã Có Attempt"}
                        """))
                .andExpect(status().isBadRequest());

        // 4. Giảng viên cố thêm câu hỏi -> 400 Bad Request
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"questionId": "%s", "scoreWeight": 1.0}
                        """.formatted(savedQ.getQuestionId())))
                .andExpect(status().isBadRequest());

        // 5. Giảng viên cố gỡ câu hỏi -> 400 Bad Request
        mockMvc.perform(delete("/api/v1/exams/" + examId + "/questions/" + savedQ.getQuestionId())
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isBadRequest());

        // 6. Giảng viên cố xóa kỳ thi -> 400 Bad Request
        mockMvc.perform(delete("/api/v1/exams/" + examId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // KỊCH BẢN 13: STUDENT không được tạo/sửa bài thi
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 13: STUDENT không được tạo/sửa bài thi")
    void test13_StudentCannotCreateOrModifyExam() throws Exception {
        // Sinh viên tạo bài thi -> 403 Forbidden
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Student Exam",
                            "examType": "PRACTICE",
                            "durationMinutes": 10
                        }
                        """.formatted(class1Id)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // KỊCH BẢN 14: Các ClassSettings cũ vẫn hoạt động
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 14: Các ClassSettings cũ vẫn hoạt động")
    void test14_ClassSettingsOperateNormally() throws Exception {
        // 1. Lấy ClassSettings hiện tại
        mockMvc.perform(get("/api/v1/classes/" + class1Id + "/settings")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.practiceEnabled").value(true));

        // 2. Giảng viên cập nhật ClassSettings
        mockMvc.perform(put("/api/v1/classes/" + class1Id + "/settings")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "practiceEnabled": true,
                            "quizEnabled": true,
                            "maxPracticeAttempts": 15,
                            "practiceShowSolution": true
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxPracticeAttempts").value(15))
                .andExpect(jsonPath("$.data.practiceShowSolution").value(true));
    }

    // =========================================================================
    // KỊCH BẢN 15: Chấm MCQ/FILL_BLANK không bị ảnh hưởng
    // =========================================================================
    @Test
    @DisplayName("Kịch bản 15: Chấm MCQ/FILL_BLANK không bị ảnh hưởng")
    void test15_GradingMcqAndFillBlankWorksAccurately() throws Exception {
        // 1. Tạo 1 câu hỏi MCQ_SINGLE
        QuestionBank mcqQ = QuestionBank.builder()
                .subjectId(subjectId)
                .topicId(testTopicId)
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Vận tốc bằng quãng đường chia cho?")
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        QuestionBank savedMcq = questionBankRepository.save(mcqQ);

        QuestionOption optA = questionOptionRepository.save(QuestionOption.builder()
                .questionId(savedMcq.getQuestionId())
                .optionText("Thời gian")
                .isCorrect(true)
                .orderIndex(1)
                .build());
        QuestionOption optB = questionOptionRepository.save(QuestionOption.builder()
                .questionId(savedMcq.getQuestionId())
                .optionText("Gia tốc")
                .isCorrect(false)
                .orderIndex(2)
                .build());

        // 2. Tạo 1 câu hỏi FILL_BLANK
        QuestionBank blankQ = QuestionBank.builder()
                .subjectId(subjectId)
                .topicId(testTopicId)
                .questionType(QuestionType.FILL_BLANK)
                .content("Đơn vị đo của lực trong hệ SI là _____.")
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        QuestionBank savedBlank = questionBankRepository.save(blankQ);

        questionAcceptedAnswerRepository.save(QuestionAcceptedAnswer.builder()
                .questionId(savedBlank.getQuestionId())
                .acceptedText("Newton")
                .orderIndex(1)
                .build());

        // 3. Giảng viên tạo đề thi PRACTICE
        String examRes = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "classId": "%s",
                            "title": "Đề Thi Kiểm Tra Tự Động Chấm",
                            "examType": "PRACTICE",
                            "durationMinutes": 45
                        }
                        """.formatted(class1Id)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String examId = objectMapper.readTree(examRes).get("data").get("examId").asText();

        // Gán 2 câu hỏi (weight 5.0 mỗi câu)
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"questionId": "%s", "scoreWeight": 5.0, "orderIndex": 1}
                        """.formatted(savedMcq.getQuestionId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"questionId": "%s", "scoreWeight": 5.0, "orderIndex": 2}
                        """.formatted(savedBlank.getQuestionId())))
                .andExpect(status().isOk());

        // 4. Sinh viên bắt đầu làm bài
        String attRes = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(attRes).get("data").get("attemptId").asText();

        // 5. Sinh viên nộp đáp án câu 1 (MCQ chọn optA - đúng)
        mockMvc.perform(post("/api/v1/exams/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "questionId": "%s",
                            "selectedOptionIds": ["%s"]
                        }
                        """.formatted(savedMcq.getQuestionId(), optA.getOptionId())))
                .andExpect(status().isOk());

        // 6. Sinh viên nộp đáp án câu 2 (FILL_BLANK điền "newton" - chuẩn hóa đúng)
        mockMvc.perform(post("/api/v1/exams/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "questionId": "%s",
                            "answerText": "newton"
                        }
                        """.formatted(savedBlank.getQuestionId())))
                .andExpect(status().isOk());

        // 7. Sinh viên nộp bài thi
        mockMvc.perform(post("/api/v1/exams/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADED"))
                .andExpect(jsonPath("$.data.totalScore").value(10.0));
    }
}
