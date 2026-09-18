package com.vatly1.example.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.vatly1.example.app.JwtAuthServiceApp;
import java.util.UUID;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClassControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String instructorToken;
    private String taToken;
    private String studentToken;
    
    private String subjectId;
    private String semesterId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        taToken = signin("ta_hung", "ta_hung123456");
        studentToken = signin("sv_an", "sv_an123456");

        // Fetch a valid subject and semester for testing
        String subjectRes = mockMvc.perform(get("/api/v1/subjects?page=0&size=1")
                .header("Authorization", "Bearer " + adminToken))
            .andReturn().getResponse().getContentAsString();
        subjectId = objectMapper.readTree(subjectRes).get("data").get("content").get(0).get("subjectId").asText();

        String semesterRes = mockMvc.perform(get("/api/v1/semesters")
                .header("Authorization", "Bearer " + adminToken))
            .andReturn().getResponse().getContentAsString();
        semesterId = objectMapper.readTree(semesterRes).get("data").get(0).get("semesterId").asText();
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

    private String getUserId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("userId").asText();
    }

    @Test
    void getClasses_returnsPagedData() throws Exception {
        mockMvc.perform(get("/api/v1/classes?page=0&size=10")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void createClass_asInstructor_success() throws Exception {
        String classCode = "CLASS_" + UUID.randomUUID().toString().substring(0, 8);
        String bodyContent = """
            {
                "subjectId": "%s",
                "semesterId": "%s",
                "classCode": "%s",
                "maxStudents": 50
            }
            """.formatted(subjectId, semesterId, classCode);

        mockMvc.perform(post("/api/v1/classes")
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyContent))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.classCode").value(classCode));
    }

    @Test
    void createClass_asStudent_returns403() throws Exception {
        String bodyContent = """
            {
                "subjectId": "%s",
                "semesterId": "%s",
                "classCode": "INVALID_CLASS",
                "maxStudents": 50
            }
            """.formatted(subjectId, semesterId);

        mockMvc.perform(post("/api/v1/classes")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyContent))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateClassStatus_asAdmin_success() throws Exception {
        // Create a class first
        String classCode = "STS_" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
                "subjectId": "%s",
                "semesterId": "%s",
                "classCode": "%s",
                "maxStudents": 50
            }
            """.formatted(subjectId, semesterId, classCode);

        String createRes = mockMvc.perform(post("/api/v1/classes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andReturn().getResponse().getContentAsString();
        
        String classId = objectMapper.readTree(createRes).get("data").get("classId").asText();

        // Update status to ACTIVE
        String updateBody = "{\"status\": \"ACTIVE\"}";
        mockMvc.perform(put("/api/v1/classes/" + classId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void assignStaff_and_removeStaff_success() throws Exception {
        // Create class
        String createBody = """
            {
                "subjectId": "%s",
                "semesterId": "%s",
                "classCode": "STAFF_TEST",
                "maxStudents": 50
            }
            """.formatted(subjectId, semesterId);
        String createRes = mockMvc.perform(post("/api/v1/classes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andReturn().getResponse().getContentAsString();
        String classId = objectMapper.readTree(createRes).get("data").get("classId").asText();
        String taUserId = getUserId(taToken);

        // Assign TA
        String assignBody = """
            {
                "userId": "%s",
                "roleInClass": "TA"
            }
            """.formatted(taUserId);
        mockMvc.perform(post("/api/v1/classes/" + classId + "/staff")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignBody))
            .andExpect(status().isCreated());

        // Verify Staff exists
        mockMvc.perform(get("/api/v1/classes/" + classId + "/staff")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        // Remove Staff
        mockMvc.perform(delete("/api/v1/classes/" + classId + "/staff/" + taUserId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void enrollSingleStudent_and_removeStudent_success() throws Exception {
        // Create class
        String createBody = """
            {
                "subjectId": "%s",
                "semesterId": "%s",
                "classCode": "STU_TEST",
                "maxStudents": 50
            }
            """.formatted(subjectId, semesterId);
        String createRes = mockMvc.perform(post("/api/v1/classes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andReturn().getResponse().getContentAsString();
        String classId = objectMapper.readTree(createRes).get("data").get("classId").asText();
        String stuUserId = getUserId(studentToken);

        // Update class status to ACTIVE
        String activeStatusBody = "{\"status\": \"ACTIVE\"}";
        mockMvc.perform(put("/api/v1/classes/" + classId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(activeStatusBody))
            .andExpect(status().isOk());

        // Enroll Student
        String enrollBody = """
            {
                "studentId": "%s"
            }
            """.formatted(stuUserId);
        mockMvc.perform(post("/api/v1/classes/" + classId + "/enroll-single")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody))
            .andExpect(status().isCreated());

        // Verify Student exists
        mockMvc.perform(get("/api/v1/classes/" + classId + "/students?page=0&size=10")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

        // Update Enrollment Status
        String statusBody = "{\"status\": \"DROPPED\"}";
        mockMvc.perform(put("/api/v1/classes/" + classId + "/students/" + stuUserId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusBody))
            .andExpect(status().isOk());

        // Remove Student
        mockMvc.perform(delete("/api/v1/classes/" + classId + "/students/" + stuUserId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CLS-01: Ghi danh sinh viên vào lớp đang ở trạng thái DRAFT -> phải bị từ chối 400")
    void enrollStudent_intoDraftClass_returns400() throws Exception {
        // Tạo lớp mới (mặc định status = DRAFT)
        String classCode = "DRAFT_" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
                "subjectId": "%s",
                "semesterId": "%s",
                "classCode": "%s",
                "maxStudents": 30
            }
            """.formatted(subjectId, semesterId, classCode);

        String createRes = mockMvc.perform(post("/api/v1/classes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String classId = objectMapper.readTree(createRes).get("data").get("classId").asText();

        String stuUserId = getUserId(studentToken);
        String enrollBody = """
            {
                "studentId": "%s"
            }
            """.formatted(stuUserId);

        mockMvc.perform(post("/api/v1/classes/" + classId + "/enroll-single")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CLS-02: Ghi danh sinh viên vào lớp đã đóng (ARCHIVED/COMPLETED) -> phải bị từ chối 400")
    void enrollStudent_intoArchivedClass_returns400() throws Exception {
        String classCode = "ARCH_" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
                "subjectId": "%s",
                "semesterId": "%s",
                "classCode": "%s",
                "maxStudents": 30
            }
            """.formatted(subjectId, semesterId, classCode);

        String createRes = mockMvc.perform(post("/api/v1/classes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String classId = objectMapper.readTree(createRes).get("data").get("classId").asText();

        // Chuyển sang ACTIVE rồi sang COMPLETED (lớp kết thúc/đóng)
        mockMvc.perform(put("/api/v1/classes/" + classId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ACTIVE\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/classes/" + classId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"COMPLETED\"}"))
            .andExpect(status().isOk());

        String stuUserId = getUserId(studentToken);
        String enrollBody = """
            {
                "studentId": "%s"
            }
            """.formatted(stuUserId);

        mockMvc.perform(post("/api/v1/classes/" + classId + "/enroll-single")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CLS-03: Ghi danh khi lớp đã đạt số lượng tối đa (max_students) -> phải bị từ chối 400")
    void enrollStudent_whenMaxStudentsReached_returns400() throws Exception {
        // Tạo lớp với maxStudents = 1
        String classCode = "FULL_" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
                "subjectId": "%s",
                "semesterId": "%s",
                "classCode": "%s",
                "maxStudents": 1
            }
            """.formatted(subjectId, semesterId, classCode);

        String createRes = mockMvc.perform(post("/api/v1/classes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String classId = objectMapper.readTree(createRes).get("data").get("classId").asText();

        // Kích hoạt lớp thành ACTIVE
        mockMvc.perform(put("/api/v1/classes/" + classId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ACTIVE\"}"))
            .andExpect(status().isOk());

        // Sinh viên 1 ghi danh thành công (đạt 1/1)
        String stu1Id = getUserId(studentToken);
        mockMvc.perform(post("/api/v1/classes/" + classId + "/enroll-single")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\": \"%s\"}".formatted(stu1Id)))
            .andExpect(status().isCreated());

        // Sinh viên 2 (sv_binh) cố ghi danh -> phải bị từ chối 400 Bad Request
        String studentBToken = signin("sv_binh", "sv_binh123456");
        String stu2Id = getUserId(studentBToken);
        mockMvc.perform(post("/api/v1/classes/" + classId + "/enroll-single")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\": \"%s\"}".formatted(stu2Id)))
            .andExpect(status().isBadRequest());
    }
}