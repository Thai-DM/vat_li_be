package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Experiment;
import com.vatly1.example.entity.ExperimentAssignment;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.repository.ExperimentAssignmentRepository;
import com.vatly1.example.repository.ExperimentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class IdorSecurityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentAssignmentRepository assignmentRepository;

    private String studentAToken; // sv_an
    private String studentBToken; // sv_binh
    private String instructorAToken; // gv_nguyen (chủ nhiệm PHY101-01)
    private String instructorBToken; // gv_tran (chủ nhiệm PHY101-02)
    private User userA;
    private User userB;

    private Class classAOnly; // PHY101-02 (sv_an enrolled, sv_binh NOT enrolled)
    private Class sharedClass; // PHY101-01 (both enrolled)
    private Topic testTopic;
    private ExperimentAssignment assignmentClassAOnly;

    @BeforeEach
    void setUp() throws Exception {
        studentAToken = signin("sv_an", "sv_an123456");
        studentBToken = signin("sv_binh", "sv_binh123456");
        instructorAToken = signin("gv_nguyen", "gv_nguyen123456");
        instructorBToken = signin("gv_tran", "gv_tran123456");

        userA = userRepository.findByUsername("sv_an");
        userB = userRepository.findByUsername("sv_binh");

        sharedClass = classRepository.findAll().stream()
                .filter(c -> "PHY101-01".equals(c.getClassCode()))
                .findFirst().orElse(null);
        classAOnly = classRepository.findAll().stream()
                .filter(c -> "PHY101-02".equals(c.getClassCode()))
                .findFirst().orElse(null);

        Subject phy101 = subjectRepository.findAll().stream()
                .filter(s -> "PHY101".equals(s.getSubjectCode()))
                .findFirst().orElse(null);

        if (phy101 != null) {
            testTopic = topicRepository.save(Topic.builder()
                    .subjectId(phy101.getSubjectId())
                    .topicName("IDOR Topic Test " + UUID.randomUUID())
                    .orderIndex(1)
                    .build());

            Experiment exp = experimentRepository.save(Experiment.builder()
                    .subjectId(phy101.getSubjectId())
                    .title("IDOR Test Experiment " + UUID.randomUUID())
                    .description("Test experiment")
                    .orderIndex(1)
                    .build());

            if (classAOnly != null) {
                assignmentClassAOnly = assignmentRepository.save(ExperimentAssignment.builder()
                        .experimentId(exp.getExperimentId())
                        .classId(classAOnly.getClassId())
                        .assignedBy(userA.getUserId())
                        .dueDate(Instant.now().plusSeconds(86400))
                        .createdAt(Instant.now())
                        .build());
            }
        }
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
    @DisplayName("IDOR-01: Sinh viên không được truy cập endpoint progress của lớp dành cho staff")
    void studentCannotAccessClassProgressStaffEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/classes/" + sharedClass.getClassId() + "/progress")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-02: Sinh viên B không được phép cập nhật tiến độ học tập cho lớp chỉ có Sinh viên A học")
    void studentCannotUpdateProgressForClassNotEnrolled() throws Exception {
        String progressPayload = """
                {
                    "classId": "%s",
                    "topicId": "%s",
                    "progressPercent": 75.0
                }
                """.formatted(classAOnly.getClassId(), testTopic.getTopicId());

        mockMvc.perform(put("/api/v1/students/me/progress")
                        .header("Authorization", "Bearer " + studentBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(progressPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-03: Danh sách lớp của Sinh viên B không được chứa lớp mà B không ghi danh")
    void studentCannotSeeOtherStudentClassesInMyClasses() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/classes?page=0&size=20")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("PHY101-02"))));
    }

    @Test
    @DisplayName("IDOR-04: Sinh viên A không được xem thông tin chi tiết user của Sinh viên B qua path variable")
    void studentCannotAccessOtherUserProfileByUsername() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userB.getUsername())
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-05: Sinh viên A không được xem profile của Sinh viên B qua endpoint admin")
    void studentCannotAccessOtherUserProfileViaAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/users/admin/users/" + userB.getUserId() + "/profile")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-06: Sinh viên A không được phép sửa tài khoản của Sinh viên B qua endpoint admin")
    void studentCannotModifyOtherUserAccount() throws Exception {
        String updatePayload = """
                {
                    "email": "hacked_b@email.com",
                    "role": "STUDENT"
                }
                """;

        mockMvc.perform(put("/api/v1/users/admin/users/" + userB.getUserId())
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-07: Sinh viên A không được phép xóa tài khoản của Sinh viên B")
    void studentCannotDeleteOtherUserAccount() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + userB.getUsername())
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-08: Sinh viên A không được xem danh sách sinh viên của lớp qua endpoint quản lý lớp")
    void studentCannotViewClassStudentsList() throws Exception {
        mockMvc.perform(get("/api/v1/classes/" + sharedClass.getClassId() + "/students")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-09: Sinh viên A không được xóa Sinh viên B ra khỏi lớp học")
    void studentCannotRemoveOtherStudentFromClass() throws Exception {
        mockMvc.perform(delete("/api/v1/classes/" + sharedClass.getClassId() + "/students/" + userB.getUserId())
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-10: Sinh viên A không được thay đổi trạng thái ghi danh của Sinh viên B")
    void studentCannotChangeOtherStudentEnrollmentStatus() throws Exception {
        String statusPayload = """
                {
                    "status": "DROPPED"
                }
                """;

        mockMvc.perform(put("/api/v1/classes/" + sharedClass.getClassId() + "/students/" + userB.getUserId() + "/status")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-11: Sinh viên không được tự ý ghi danh sinh viên khác vào lớp")
    void studentCannotEnrollOtherStudentIntoClass() throws Exception {
        String enrollPayload = """
                {
                    "studentId": "%s"
                }
                """.formatted(userB.getUserId());

        mockMvc.perform(post("/api/v1/classes/" + sharedClass.getClassId() + "/enroll-single")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-12: Sinh viên B không được nộp bài thí nghiệm vào assignment của lớp mà B không học")
    void studentCannotSubmitExperimentForUnenrolledClassAssignment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evidence.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "dummy evidence pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/experiments/assignments/" + assignmentClassAOnly.getAssignmentId() + "/submit")
                        .file(file)
                        .param("evidenceUrl", "http://example.com/evidence")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-13: Giảng viên B (gv_tran) không được sửa thông tin lớp do Giảng viên A (gv_nguyen) phụ trách")
    void instructorCannotUpdateOtherInstructorClass() throws Exception {
        String updatePayload = """
                {
                    "classCode": "PHY101-HACKED",
                    "maxStudents": 99,
                    "description": "Lớp bị sửa trái phép"
                }
                """;

        mockMvc.perform(put("/api/v1/classes/" + sharedClass.getClassId())
                        .header("Authorization", "Bearer " + instructorBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-14: Giảng viên B không được phân công trợ giảng/nhân sự cho lớp của Giảng viên A")
    void instructorCannotAssignStaffToOtherInstructorClass() throws Exception {
        User taUser = userRepository.findByUsername("ta_hung");
        String assignPayload = """
                {
                    "userId": "%s",
                    "roleInClass": "TA"
                }
                """.formatted(taUser != null ? taUser.getUserId() : UUID.randomUUID());

        mockMvc.perform(post("/api/v1/classes/" + sharedClass.getClassId() + "/staff")
                        .header("Authorization", "Bearer " + instructorBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR-15: Giảng viên B không được xem danh sách nhân sự nội bộ của lớp do Giảng viên A phụ trách")
    void instructorCannotViewOtherInstructorClassStaff() throws Exception {
        mockMvc.perform(get("/api/v1/classes/" + sharedClass.getClassId() + "/staff")
                        .header("Authorization", "Bearer " + instructorBToken))
                .andExpect(status().isForbidden());
    }
}