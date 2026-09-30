package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String instructorToken;
    private String studentAnToken;
    private String studentBinhToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        studentAnToken = signin("sv_an", "sv_an123456");
        studentBinhToken = signin("sv_binh", "sv_binh123456");
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
        JsonNode jsonNode = objectMapper.readTree(body);
        return jsonNode.get("data").get("accessToken").asText();
    }

    @Test
    void getStudentByUsername_asAdmin_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/sv_an")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username").value("sv_an"))
                .andExpect(jsonPath("$.data.studentCode").value("SV001"))
                .andExpect(jsonPath("$.data.fullName").value("Lê Văn An"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.enrolledClasses", notNullValue()))
                .andExpect(jsonPath("$.data.enrolledClasses", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void getStudentByUsername_asInstructor_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/sv_an")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username").value("sv_an"))
                .andExpect(jsonPath("$.data.studentCode").value("SV001"));
    }

    @Test
    void getStudentByUsername_asSelf_success() throws Exception {
        // Sinh viên sv_an tự xem thông tin của mình
        mockMvc.perform(get("/api/v1/students/sv_an")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("sv_an"));
    }

    @Test
    void getStudentByUsername_byStudentCodeFallback_success() throws Exception {
        // Tra cứu bằng mã sinh viên SV001 thay vì username sv_an
        mockMvc.perform(get("/api/v1/students/SV001")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("sv_an"))
                .andExpect(jsonPath("$.data.studentCode").value("SV001"));
    }

    @Test
    void getStudentByCode_endpoint_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/by-code/SV001")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("sv_an"))
                .andExpect(jsonPath("$.data.studentCode").value("SV001"));
    }

    @Test
    void getStudentByUsername_userAliasEndpoint_success() throws Exception {
        // Test alias dưới endpoint /api/v1/users/students/{username}
        mockMvc.perform(get("/api/v1/users/students/sv_an")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("sv_an"))
                .andExpect(jsonPath("$.data.studentCode").value("SV001"));
    }

    @Test
    void getStudentByUsername_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/students/non_existing_student_999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getStudentByUsername_notAStudent_returns400() throws Exception {
        // Tài khoản admin không phải là student
        mockMvc.perform(get("/api/v1/students/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void searchStudent_asInstructor_byUsernameQueryParam_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/search?username=sv_an")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username").value("sv_an"))
                .andExpect(jsonPath("$.data.studentCode").value("SV001"))
                .andExpect(jsonPath("$.data.fullName").value("Lê Văn An"));
    }

    @Test
    void searchStudent_asInstructor_byRootWithParam_success() throws Exception {
        mockMvc.perform(get("/api/v1/students?username=sv_an")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username").value("sv_an"));
    }

    @Test
    void searchStudent_asInstructor_caseInsensitiveUsername_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/search?username=SV_AN")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username").value("sv_an"));
    }

    @Test
    void searchStudent_asInstructor_byStudentCodeQueryParam_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/search?studentCode=SV001")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username").value("sv_an"))
                .andExpect(jsonPath("$.data.studentCode").value("SV001"));
    }

    @Test
    void searchStudent_noParams_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/students/search")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void searchUser_asInstructor_underUsersPath_success() throws Exception {
        // Giảng viên tìm kiếm user theo username tại /api/v1/users/{username}
        mockMvc.perform(get("/api/v1/users/sv_an")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username").value("sv_an"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }

    @Test
    void searchUserParam_asInstructor_underUsersSearch_success() throws Exception {
        // Giảng viên tìm kiếm user theo query param tại /api/v1/users/search?username=sv_an
        mockMvc.perform(get("/api/v1/users/search?username=sv_an")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username").value("sv_an"));
    }

    @Test
    void searchStudentParam_asInstructor_underUsersStudentsSearch_success() throws Exception {
        // Giảng viên tìm kiếm chi tiết sinh viên tại /api/v1/users/students/search?username=sv_an
        mockMvc.perform(get("/api/v1/users/students/search?username=sv_an")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username").value("sv_an"))
                .andExpect(jsonPath("$.data.studentCode").value("SV001"));
    }

    @Test
    void searchUser_asStudent_returns403() throws Exception {
        // Sinh viên không có quyền tra cứu user qua /api/v1/users/{username}
        mockMvc.perform(get("/api/v1/users/sv_binh")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden());
    }
}

