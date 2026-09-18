package com.vatly1.example.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
class SubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
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
    void getAllSubjects_returnsPagedData() throws Exception {
        mockMvc.perform(get("/api/v1/subjects?page=0&size=10")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void getSubjectById_returnsSubject() throws Exception {
        String body = mockMvc.perform(get("/api/v1/subjects")
                .header("Authorization", "Bearer " + adminToken))
            .andReturn().getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(body);
        String subjectId = jsonNode.get("data").get("content").get(0).get("subjectId").asText();

        mockMvc.perform(get("/api/v1/subjects/" + subjectId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.subjectId").value(subjectId));
    }

    @Test
    void createSubject_asAdmin_success() throws Exception {
        String newSubjectCode = "SUB_" + UUID.randomUUID().toString().substring(0, 8);
        String bodyContent = """
            {
                "subjectCode": "%s",
                "subjectName": "Test Subject",
                "description": "A subject for testing"
            }
            """.formatted(newSubjectCode);

        mockMvc.perform(post("/api/v1/subjects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyContent))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.subjectCode").value(newSubjectCode));
    }

    @Test
    void createSubject_asStudent_returns403() throws Exception {
        String bodyContent = """
            {
                "subjectCode": "INVALID",
                "subjectName": "Should fail",
                "description": "Student cannot create"
            }
            """;

        mockMvc.perform(post("/api/v1/subjects")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyContent))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateSubject_asAdmin_success() throws Exception {
        String newSubjectCode = "UP_SUB_" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
                "subjectCode": "%s",
                "subjectName": "To Update",
                "description": "Old"
            }
            """.formatted(newSubjectCode);

        String createRes = mockMvc.perform(post("/api/v1/subjects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andReturn().getResponse().getContentAsString();
            
        String subjectId = objectMapper.readTree(createRes).get("data").get("subjectId").asText();

        String updateBody = """
            {
                "subjectName": "Updated Name",
                "description": "New description"
            }
            """;

        mockMvc.perform(put("/api/v1/subjects/" + subjectId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.subjectName").value("Updated Name"))
            .andExpect(jsonPath("$.data.description").value("New description"));
    }

    @Test
    void toggleSubjectStatus_asAdmin_success() throws Exception {
        String newSubjectCode = "TOG_SUB_" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
                "subjectCode": "%s",
                "subjectName": "To Toggle",
                "description": "Testing toggle"
            }
            """.formatted(newSubjectCode);

        String createRes = mockMvc.perform(post("/api/v1/subjects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andReturn().getResponse().getContentAsString();
            
        JsonNode data = objectMapper.readTree(createRes).get("data");
        String subjectId = data.get("subjectId").asText();
        boolean initialStatus = data.get("isActive").asBoolean();

        mockMvc.perform(put("/api/v1/subjects/" + subjectId + "/toggle-status")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isActive").value(!initialStatus));
    }
}