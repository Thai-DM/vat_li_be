package com.vatly1.example.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class ClassScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String instructorToken;
    private String studentToken;

    private String classId;
    private String studentId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        studentToken = signin("sv_an", "sv_an123456");

        studentId = getUserId(studentToken);

        // Fetch an existing class managed by gv_nguyen or create one
        String classesRes = mockMvc.perform(get("/api/v1/classes?page=0&size=1")
                .header("Authorization", "Bearer " + instructorToken))
            .andReturn().getResponse().getContentAsString();
        JsonNode classContent = objectMapper.readTree(classesRes).get("data").get("content");
        if (classContent != null && classContent.size() > 0) {
            classId = classContent.get(0).get("classId").asText();
        } else {
            // Fetch subject and semester to create one
            String subjectRes = mockMvc.perform(get("/api/v1/subjects?page=0&size=1")
                    .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
            String subjectId = objectMapper.readTree(subjectRes).get("data").get("content").get(0).get("subjectId").asText();

            String semesterRes = mockMvc.perform(get("/api/v1/semesters")
                    .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
            String semesterId = objectMapper.readTree(semesterRes).get("data").get(0).get("semesterId").asText();

            String classCode = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
            String bodyContent = """
                {
                    "subjectId": "%s",
                    "semesterId": "%s",
                    "classCode": "%s",
                    "maxStudents": 50
                }
                """.formatted(subjectId, semesterId, classCode);

            String createClassRes = mockMvc.perform(post("/api/v1/classes")
                    .header("Authorization", "Bearer " + instructorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyContent))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
            classId = objectMapper.readTree(createClassRes).get("data").get("classId").asText();
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

    private String getUserId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("userId").asText();
    }

    @Test
    @DisplayName("Tạo lịch học mới cho lớp - Giảng viên thành công")
    void createSchedule_asInstructor_success() throws Exception {
        String scheduleBody = """
            {
                "dayOfWeek": 2,
                "startPeriod": 1,
                "endPeriod": 3,
                "startTime": "07:00:00",
                "endTime": "09:30:00",
                "room": "A1-203",
                "building": "Nhà A1",
                "lessonType": "THEORY",
                "notes": "Lý thuyết cơ học"
            }
            """;

        mockMvc.perform(post("/api/v1/classes/" + classId + "/schedules")
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scheduleBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.scheduleId").isNotEmpty())
            .andExpect(jsonPath("$.data.dayOfWeek").value(2))
            .andExpect(jsonPath("$.data.dayOfWeekText").value("Thứ Hai"))
            .andExpect(jsonPath("$.data.room").value("A1-203"));
    }

    @Test
    @DisplayName("Sinh viên không có quyền tự tạo lịch học - 403 Forbidden")
    void createSchedule_asStudent_forbidden() throws Exception {
        String scheduleBody = """
            {
                "dayOfWeek": 3,
                "startPeriod": 4,
                "endPeriod": 6,
                "room": "Lab 101"
            }
            """;

        mockMvc.perform(post("/api/v1/classes/" + classId + "/schedules")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scheduleBody))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lấy danh sách lịch học của lớp - Thành công")
    void getSchedulesByClassId_success() throws Exception {
        mockMvc.perform(get("/api/v1/classes/" + classId + "/schedules")
                .header("Authorization", "Bearer " + instructorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Cập nhật và xóa lịch học - Thành công")
    void updateAndDeleteSchedule_success() throws Exception {
        // 1. Tạo lịch học
        String createBody = """
            {
                "dayOfWeek": 4,
                "startPeriod": 2,
                "endPeriod": 4,
                "room": "B2-101",
                "lessonType": "LAB"
            }
            """;
        String res = mockMvc.perform(post("/api/v1/classes/" + classId + "/schedules")
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String scheduleId = objectMapper.readTree(res).get("data").get("scheduleId").asText();

        // 2. Cập nhật phòng học
        String updateBody = """
            {
                "room": "B2-202",
                "notes": "Chuyển phòng sang tầng 2"
            }
            """;
        mockMvc.perform(put("/api/v1/classes/schedules/" + scheduleId)
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.room").value("B2-202"))
            .andExpect(jsonPath("$.data.notes").value("Chuyển phòng sang tầng 2"));

        // 3. Xóa lịch học
        mockMvc.perform(delete("/api/v1/classes/schedules/" + scheduleId)
                .header("Authorization", "Bearer " + instructorToken))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sinh viên xem thời khóa biểu của mình - GET /api/v1/students/me/schedule")
    void getMySchedule_asStudent_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/schedule")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Admin xem thời khóa biểu của sinh viên bất kỳ - param studentId")
    void getSchedule_asAdmin_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/schedule?studentId=" + studentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }
}
