package com.vatly1.example.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.SystemSetting;
import com.vatly1.example.filter.RateLimitFilter;
import com.vatly1.example.repository.ISystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemSettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ISystemSettingRepository settingRepository;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        RateLimitFilter.reset();
        adminToken = signin("admin", "admin123456");
        studentToken = signin("sv_an", "sv_an123456");
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
    @DisplayName("SET-01: Admin GET /api/v1/admin/settings -> 200 OK danh sách setting")
    void testSET01_AdminGetAllSettings_ReturnsList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$.data[0].settingKey").exists())
                .andExpect(jsonPath("$.data[0].settingValue").exists());
    }

    @Test
    @DisplayName("SET-02: Admin GET /api/v1/admin/settings/{key} với key tồn tại -> 200 OK")
    void testSET02_AdminGetSettingByKey_Existing_ReturnsSetting() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/exam.max_attempts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settingKey").value("exam.max_attempts"))
                .andExpect(jsonPath("$.data.settingValue").value("3"));
    }

    @Test
    @DisplayName("SET-03: Admin GET /api/v1/admin/settings/{key} với key không tồn tại -> 404 Not Found")
    void testSET03_AdminGetSettingByKey_NonExisting_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/non_existing_key_xyz")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SET-04: Admin PUT /api/v1/admin/settings/{key} -> 200 OK và persist DB")
    void testSET04_AdminUpdateSetting_Success() throws Exception {
        String updatePayload = """
                {
                    "settingValue": "5",
                    "description": "Cap nhat so lan lam bai thanh 5"
                }
                """;

        mockMvc.perform(put("/api/v1/admin/settings/exam.max_attempts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settingKey").value("exam.max_attempts"))
                .andExpect(jsonPath("$.data.settingValue").value("5"));

        SystemSetting setting = settingRepository.findBySettingKey("exam.max_attempts").orElseThrow();
        assertEquals("5", setting.getSettingValue());
    }

    @Test
    @DisplayName("SET-05: Student / Instructor PUT /api/v1/admin/settings/{key} -> 403 Forbidden")
    void testSET05_StudentUpdateSetting_Returns403Forbidden() throws Exception {
        String updatePayload = """
                {
                    "settingValue": "99",
                    "description": "Hack setting"
                }
                """;

        mockMvc.perform(put("/api/v1/admin/settings/exam.max_attempts")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isForbidden());
    }
}