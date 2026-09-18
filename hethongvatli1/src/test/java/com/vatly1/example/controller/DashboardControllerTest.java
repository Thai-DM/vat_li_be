package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.User;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    private String adminToken;
    private String instructorToken;
    private String otherInstructorToken;
    private String studentToken;
    private String classId;
    private UUID studentAId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        otherInstructorToken = signin("gv_tran", "gv_tran123456");
        studentToken = signin("sv_an", "sv_an123456");

        User svAn = userRepository.findByUsername("sv_an");
        studentAId = svAn.getUserId();

        User gvNguyen = userRepository.findByUsername("gv_nguyen");
        Class targetClass = classRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getInstructorId(), gvNguyen.getUserId()) && "PHY101-01".equals(c.getClassCode()))
                .findFirst()
                .orElseThrow();
        classId = targetClass.getClassId().toString();
    }

    private String signin(String username, String password) throws Exception {
        String bodyContent = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String body = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyContent))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("DASH-01: Admin POST /dashboard/class/{id}/regenerate -> 200, tạo snapshot mới")
    void testDash01_AdminRegenerateSnapshot() throws Exception {
        mockMvc.perform(post("/api/v1/dashboard/class/" + classId + "/regenerate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.classId").value(classId))
                .andExpect(jsonPath("$.data.data.avgScore").exists())
                .andExpect(jsonPath("$.data.data.completedTopics").exists())
                .andExpect(jsonPath("$.data.data.labsConfirmed").exists());
    }

    @Test
    @DisplayName("DASH-02: GV phụ trách GET /dashboard/class/{id} -> 200, trả data_json")
    void testDash02_InstructorGetClassDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/class/" + classId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.classId").value(classId))
                .andExpect(jsonPath("$.data.data").exists())
                .andExpect(jsonPath("$.data.data.avgScore").isNumber());
    }

    @Test
    @DisplayName("DASH-03: IDOR: GV không phụ trách lớp GET /dashboard/class/{id} -> 403")
    void testDash03_OtherInstructorGetClassDashboardForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/class/" + classId)
                        .header("Authorization", "Bearer " + otherInstructorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DASH-04: SV GET /dashboard/me -> 200, trả snapshot cá nhân")
    void testDash04_StudentGetMyDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.data").exists());
    }

    @Test
    @DisplayName("DASH-05: SV GET /dashboard/class/{id}/student/{studentId} -> 403 Forbidden")
    void testDash05_StudentGetStudentDashboardForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/class/" + classId + "/student/" + studentAId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
