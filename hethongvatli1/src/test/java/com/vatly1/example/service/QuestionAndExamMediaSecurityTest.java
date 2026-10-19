package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.controller.FileController;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.FileUpload;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.FileProcessingStatus;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ExamAttemptDTO;
import com.vatly1.example.model.dto.ExamDTO;
import com.vatly1.example.model.dto.ExamQuestionDetailDTO;
import com.vatly1.example.model.dto.QuestionBankDTO;
import com.vatly1.example.model.dto.StudentExamQuestionDTO;
import com.vatly1.example.model.request.AddExamQuestionDTO;
import com.vatly1.example.model.request.CreateExamDTO;
import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.repository.FileUploadRepository;
import com.vatly1.example.repository.IClassRepository;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class QuestionAndExamMediaSecurityTest {

    @Autowired
    private IExamService examService;

    @Autowired
    private IQuestionBankService questionBankService;

    @Autowired
    private com.vatly1.example.service.IFileStorageService fileStorageService;

    @Autowired
    private FileController fileController;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IUserRepository userRepository;

    private User teacher1;
    private User teacher2;
    private User studentEnrolled;
    private User studentOutsider;
    private Topic topic;
    private Class clazz;

    @BeforeEach
    void setUp() {
        teacher1 = userRepository.findByUsername("gv_nguyen");
        teacher2 = userRepository.findByUsername("gv_tran");
        studentEnrolled = userRepository.findByUsername("sv_an");
        studentOutsider = userRepository.findByUsername("sv_outsider_media");
        if (studentOutsider == null) {
            studentOutsider = userRepository.save(User.builder()
                    .username("sv_outsider_media")
                    .email("outsider_media@test.com")
                    .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz12345678901234567890123456")
                    .role(UserRole.STUDENT)
                    .status(UserStatus.ACTIVE)
                    .build());
        }

        // Sử dụng lớp học đã có của gv_nguyen mà sv_an đã được ghi danh trong hệ thống test
        clazz = classRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getInstructorId(), teacher1.getUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Class for gv_nguyen not found"));

        topic = topicRepository.save(Topic.builder()
                .subjectId(clazz.getSubjectId())
                .topicName("Chủ đề Mạch dao động LC " + UUID.randomUUID().toString().substring(0, 6))
                .orderIndex(1)
                .build());
    }

    @Test
    @DisplayName("1. mediaUrl được truyền đầy đủ trong ExamQuestionDetailDTO và StudentExamQuestionDTO")
    void testMediaUrlMappingInExamAndAttemptDTOs() {
        String testMediaUrl = "/api/v1/files/chunked/circuit_schematic_" + UUID.randomUUID() + ".png";

        // 1. Tạo câu hỏi có ảnh mediaUrl (ở trạng thái PENDING để có thể duyệt ngay)
        QuestionBankDTO createdQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(clazz.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Cho mạch điện như sơ đồ trong hình. Tính cảm kháng ZL:")
                .mediaUrl(testMediaUrl)
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.PENDING)
                .options(List.of(
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder().content("100 Ohm").isCorrect(true).orderIndex(0).build(),
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder().content("200 Ohm").isCorrect(false).orderIndex(1).build()
                ))
                .build(), teacher1.getUserId(), "INSTRUCTOR");

        User adminUser = userRepository.findByUsername("admin");
        // Duyệt câu hỏi
        questionBankService.approveQuestion(createdQ.getQuestionId(), adminUser.getUserId(), "ADMIN", "Phê duyệt câu hỏi có ảnh");

        // 2. Tạo đề thi
        ExamDTO examDTO = examService.createExam(CreateExamDTO.builder()
                .classId(clazz.getClassId())
                .title("Kiểm tra 15 phút Vật lý điện từ")
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(45, ChronoUnit.MINUTES))
                .build(), teacher1.getUserId());

        // 3. Thêm câu hỏi vào đề
        examService.addQuestionToExam(examDTO.getExamId(), AddExamQuestionDTO.builder()
                .questionId(createdQ.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.valueOf(10))
                .build(), teacher1.getUserId());

        // 4. Kiểm tra ExamQuestionDetailDTO chứa mediaUrl
        List<ExamQuestionDetailDTO> detailQuestions = examService.getExamQuestions(examDTO.getExamId(), teacher1.getUserId(), "INSTRUCTOR");
        assertEquals(1, detailQuestions.size());
        assertEquals(testMediaUrl, detailQuestions.get(0).getMediaUrl(), "ExamQuestionDetailDTO phải có mediaUrl chính xác");

        // 5. Sinh viên bắt đầu làm bài -> Kiểm tra StudentExamQuestionDTO chứa mediaUrl
        ExamAttemptDTO attempt = examService.startAttempt(examDTO.getExamId(), studentEnrolled.getUserId());
        List<StudentExamQuestionDTO> studentQuestions = examService.getAttemptQuestions(attempt.getAttemptId(), studentEnrolled.getUserId(), "STUDENT");
        assertEquals(1, studentQuestions.size());
        assertEquals(testMediaUrl, studentQuestions.get(0).getMediaUrl(), "StudentExamQuestionDTO phải có mediaUrl chính xác");
    }

    @Test
    @DisplayName("2. Phân quyền tải ảnh CHUNKED_UPLOAD: Cho phép giảng viên khác và sinh viên hợp lệ; chặn sinh viên ngoài lớp")
    void testFileAccessPermissionsForQuestionMedia() {
        String uniqueFileName = "circuit_img_" + UUID.randomUUID() + ".png";
        byte[] dummyBytes = "fake image circuit bytes".getBytes();
        String storedUrl = fileStorageService.storeFile(new java.io.ByteArrayInputStream(dummyBytes), uniqueFileName, dummyBytes.length, "image/png", "chunked");

        // Giảng viên 1 tải file lên
        FileUpload fileUpload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(teacher1.getUserId())
                .originalFilename(uniqueFileName)
                .storedUrl(storedUrl)
                .fileSizeBytes((long) dummyBytes.length)
                .mimeType("image/png")
                .purpose("CHUNKED_UPLOAD")
                .status(FileProcessingStatus.COMPLETED)
                .build());

        // Gắn ảnh vào câu hỏi ngân hàng câu hỏi (đã APPROVED)
        QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                .subjectId(clazz.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Xem hình sơ đồ bên dưới:")
                .mediaUrl(storedUrl)
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(teacher1.getUserId())
                .createdAt(Instant.now())
                .build());

        // Gắn vào đề thi của lớp
        Exam exam = examRepository.save(Exam.builder()
                .classId(clazz.getClassId())
                .title("Đề thi giữa kỳ có ảnh")
                .examType(ExamType.PRACTICE)
                .durationMinutes(45)
                .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(50, ChronoUnit.MINUTES))
                .createdBy(teacher1.getUserId())
                .createdAt(Instant.now())
                .build());

        examService.addQuestionToExam(exam.getExamId(), AddExamQuestionDTO.builder()
                .questionId(q.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.TEN)
                .build(), teacher1.getUserId());

        // Case A: Giảng viên 2 (không phải uploader) truy cập ảnh câu hỏi -> THÀNH CÔNG (trước đây bị 403)
        MockHttpServletRequest reqTeacher2 = new MockHttpServletRequest("GET", storedUrl);
        reqTeacher2.setAttribute("userId", teacher2.getUserId().toString());
        reqTeacher2.setAttribute("role", "INSTRUCTOR");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                teacher2.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))));

        assertDoesNotThrow(() -> fileController.getFile(reqTeacher2),
                "Giảng viên khác được quyền truy cập ảnh của câu hỏi trong ngân hàng / đề thi");

        // Case B: Sinh viên đã ghi danh vào lớp làm đề -> THÀNH CÔNG
        MockHttpServletRequest reqStudent = new MockHttpServletRequest("GET", storedUrl);
        reqStudent.setAttribute("userId", studentEnrolled.getUserId().toString());
        reqStudent.setAttribute("role", "STUDENT");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                studentEnrolled.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));

        assertDoesNotThrow(() -> fileController.getFile(reqStudent),
                "Sinh viên trong lớp được cấp quyền xem ảnh câu hỏi trong đề thi");

        // Case C1: Sinh viên ngoài lớp không thuộc môn học -> BỊ CHẶN 403 FORBIDDEN
        MockHttpServletRequest reqOutsider = new MockHttpServletRequest("GET", storedUrl);
        reqOutsider.setAttribute("userId", studentOutsider.getUserId().toString());
        reqOutsider.setAttribute("role", "STUDENT");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                studentOutsider.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));

        CustomException exStudent = assertThrows(CustomException.class, () -> fileController.getFile(reqOutsider));
        assertEquals(HttpStatus.FORBIDDEN, exStudent.getHttpStatus());

        // Case C2: Sinh viên lớp khác (sv_dung) không được phân công làm đề thi này -> BỊ CHẶN 403 FORBIDDEN
        User studentOtherClass = userRepository.findByUsername("sv_dung");
        if (studentOtherClass != null) {
            MockHttpServletRequest reqOtherClass = new MockHttpServletRequest("GET", storedUrl);
            reqOtherClass.setAttribute("userId", studentOtherClass.getUserId().toString());
            reqOtherClass.setAttribute("role", "STUDENT");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    studentOtherClass.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));

            CustomException exOtherClass = assertThrows(CustomException.class, () -> fileController.getFile(reqOtherClass));
            assertEquals(HttpStatus.FORBIDDEN, exOtherClass.getHttpStatus());
        }

        // Case D: File CHUNKED_UPLOAD riêng tư không gắn vào bất kỳ câu hỏi nào -> Giảng viên khác BỊ CHẶN 403
        String privateFileName = "private_notes_" + UUID.randomUUID() + ".png";
        byte[] privateBytes = "private content".getBytes();
        String privateStoredUrl = fileStorageService.storeFile(new java.io.ByteArrayInputStream(privateBytes), privateFileName, privateBytes.length, "image/png", "chunked");

        fileUploadRepository.save(FileUpload.builder()
                .uploaderId(teacher1.getUserId())
                .originalFilename(privateFileName)
                .storedUrl(privateStoredUrl)
                .fileSizeBytes((long) privateBytes.length)
                .mimeType("image/png")
                .purpose("CHUNKED_UPLOAD")
                .status(FileProcessingStatus.COMPLETED)
                .build());

        MockHttpServletRequest reqPrivate = new MockHttpServletRequest("GET", privateStoredUrl);
        reqPrivate.setAttribute("userId", teacher2.getUserId().toString());
        reqPrivate.setAttribute("role", "INSTRUCTOR");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                teacher2.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))));

        CustomException exPrivate = assertThrows(CustomException.class, () -> fileController.getFile(reqPrivate));
        assertEquals(HttpStatus.FORBIDDEN, exPrivate.getHttpStatus());
    }
}
