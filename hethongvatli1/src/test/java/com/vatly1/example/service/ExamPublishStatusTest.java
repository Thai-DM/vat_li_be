package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ExamAttemptDTO;
import com.vatly1.example.model.dto.ExamDTO;
import com.vatly1.example.model.request.AddExamQuestionDTO;
import com.vatly1.example.model.request.CreateExamDTO;
import com.vatly1.example.model.request.UpdateExamDTO;
import com.vatly1.example.repository.IClassRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class ExamPublishStatusTest {

    @Autowired
    private IExamService examService;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    private User instructor;
    private User student;
    private Class clazz;
    private Topic topic;
    private QuestionBank question;

    @BeforeEach
    void setUp() {
        instructor = userRepository.findByUsername("gv_nguyen");
        student = userRepository.findByUsername("sv_an");

        clazz = classRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getInstructorId(), instructor.getUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Class for gv_nguyen not found"));

        topic = topicRepository.save(Topic.builder()
                .subjectId(clazz.getSubjectId())
                .topicName("Topic Exam Publish " + UUID.randomUUID())
                .orderIndex(1)
                .build());

        question = questionBankRepository.save(QuestionBank.builder()
                .subjectId(clazz.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Nội dung câu hỏi kiểm tra công bố đề:")
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(instructor.getUserId())
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("PUB-01: Tạo đề thi mặc định hoặc chỉ định isPublished -> lưu và trả về chính xác")
    void test01_CreateExam_PublishedStatus() {
        // 1. Mặc định (isPublished = null) -> true
        ExamDTO defaultExam = examService.createExam(CreateExamDTO.builder()
                .classId(clazz.getClassId())
                .title("Đề thi mặc định")
                .examType(ExamType.PRACTICE)
                .durationMinutes(45)
                .build(), instructor.getUserId());

        assertNotNull(defaultExam.getIsPublished());
        assertTrue(defaultExam.getIsPublished(), "Đề thi tạo mặc định phải có isPublished = true");

        // 2. Chỉ định rõ ràng isPublished = false (ẩn đề thi)
        ExamDTO unpublishedExam = examService.createExam(CreateExamDTO.builder()
                .classId(clazz.getClassId())
                .title("Đề thi bản nháp ẩn")
                .examType(ExamType.PRACTICE)
                .durationMinutes(45)
                .isPublished(false)
                .build(), instructor.getUserId());

        assertNotNull(unpublishedExam.getIsPublished());
        assertFalse(unpublishedExam.getIsPublished(), "Đề thi tạo với isPublished = false phải có isPublished = false");
    }

    @Test
    @DisplayName("PUB-02: Giảng viên cập nhật ẩn/hiện đề thi qua UpdateExamDTO -> Cập nhật thành công")
    void test02_UpdateExam_TogglePublish() {
        ExamDTO exam = examService.createExam(CreateExamDTO.builder()
                .classId(clazz.getClassId())
                .title("Đề thi thay đổi trạng thái")
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .isPublished(true)
                .build(), instructor.getUserId());

        assertTrue(exam.getIsPublished());

        // 1. Ẩn đề thi
        ExamDTO updatedHide = examService.updateExam(exam.getExamId(), UpdateExamDTO.builder()
                .isPublished(false)
                .build(), instructor.getUserId());

        assertFalse(updatedHide.getIsPublished(), "Sau khi cập nhật isPublished = false, đề thi phải ở trạng thái ẩn");

        // Tra cứu lại qua getExamById
        ExamDTO fetched = examService.getExamById(exam.getExamId());
        assertFalse(fetched.getIsPublished(), "getExamById phải trả về isPublished = false");

        // 2. Hiển thị lại đề thi
        ExamDTO updatedShow = examService.updateExam(exam.getExamId(), UpdateExamDTO.builder()
                .isPublished(true)
                .build(), instructor.getUserId());

        assertTrue(updatedShow.getIsPublished(), "Sau khi bật lại isPublished = true, đề thi phải hiển thị");
    }

    @Test
    @DisplayName("PUB-03: Danh sách đề thi theo lớp: Giảng viên thấy cả ẩn và hiện; Sinh viên chỉ thấy đề đã công bố")
    void test03_FilterExamsByClass_PublishedOnlyForStudents() {
        // Tạo 1 đề hiện và 1 đề ẩn
        ExamDTO publishedExam = examService.createExam(CreateExamDTO.builder()
                .classId(clazz.getClassId())
                .title("Đề công bố công khai " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .isPublished(true)
                .build(), instructor.getUserId());

        ExamDTO hiddenExam = examService.createExam(CreateExamDTO.builder()
                .classId(clazz.getClassId())
                .title("Đề soạn thảo ẩn " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .isPublished(false)
                .build(), instructor.getUserId());

        // 1. Giảng viên xem danh sách đề lớp -> Thấy cả 2 đề
        List<ExamDTO> instructorExams = examService.getExamsByClass(clazz.getClassId(), instructor.getUserId(), "INSTRUCTOR");
        assertTrue(instructorExams.stream().anyMatch(e -> e.getExamId().equals(publishedExam.getExamId()) && Boolean.TRUE.equals(e.getIsPublished())));
        assertTrue(instructorExams.stream().anyMatch(e -> e.getExamId().equals(hiddenExam.getExamId()) && Boolean.FALSE.equals(e.getIsPublished())));

        // 2. Sinh viên xem danh sách đề lớp -> Chỉ thấy đề công bố
        List<ExamDTO> studentExams = examService.getExamsByClass(clazz.getClassId(), student.getUserId(), "STUDENT");
        assertTrue(studentExams.stream().anyMatch(e -> e.getExamId().equals(publishedExam.getExamId())));
        assertFalse(studentExams.stream().anyMatch(e -> e.getExamId().equals(hiddenExam.getExamId())),
                "Sinh viên không được nhìn thấy đề thi có isPublished = false");

        // 3. Sinh viên xem tất cả đề qua getAllExams -> Cũng chỉ thấy đề công bố
        List<ExamDTO> studentAllExams = examService.getAllExams(student.getUserId(), "STUDENT");
        assertFalse(studentAllExams.stream().anyMatch(e -> e.getExamId().equals(hiddenExam.getExamId())),
                "getAllExams của sinh viên không được chứa đề thi ẩn");
    }

    @Test
    @DisplayName("PUB-04: Sinh viên bắt đầu làm bài (startAttempt) đề thi ẩn -> Bị từ chối 403 Forbidden")
    void test04_StartAttempt_UnpublishedExam_Rejected() {
        // Tạo đề thi ẩn
        ExamDTO hiddenExam = examService.createExam(CreateExamDTO.builder()
                .classId(clazz.getClassId())
                .title("Đề thi ẩn chưa sẵn sàng " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .durationMinutes(45)
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(40, ChronoUnit.MINUTES))
                .isPublished(false)
                .build(), instructor.getUserId());

        // Thêm câu hỏi
        examService.addQuestionToExam(hiddenExam.getExamId(), AddExamQuestionDTO.builder()
                .questionId(question.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.TEN)
                .build(), instructor.getUserId());

        // Sinh viên cố tình gọi startAttempt đề ẩn -> Bị chặn 403
        CustomException ex = assertThrows(CustomException.class,
                () -> examService.startAttempt(hiddenExam.getExamId(), student.getUserId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("chưa được công bố"));

        // Giảng viên bật công bố đề thi
        examService.updateExam(hiddenExam.getExamId(), UpdateExamDTO.builder()
                .isPublished(true)
                .build(), instructor.getUserId());

        // Sinh viên bắt đầu làm bài -> Thành công!
        ExamAttemptDTO attempt = assertDoesNotThrow(
                () -> examService.startAttempt(hiddenExam.getExamId(), student.getUserId()));
        assertNotNull(attempt.getAttemptId());
    }
}
