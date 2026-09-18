package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vatly1.example.app.JwtAuthServiceApp;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TopicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String studentToken;
    private String subjectId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        studentToken = signin("sv_an", "sv_an123456");

        String subjectRes = mockMvc.perform(get("/api/v1/subjects?page=0&size=1")
                .header("Authorization", "Bearer " + adminToken))
            .andReturn().getResponse().getContentAsString();
        subjectId = objectMapper.readTree(subjectRes).get("data").get("content").get(0).get("subjectId").asText();
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
    void createTopic_asAdmin_success() throws Exception {
        String topicName = "Chương " + System.currentTimeMillis();
        String topicContent = """
            {
                "topicName": "%s",
                "orderIndex": 1,
                "description": "Nội dung cơ bản về động học"
            }
            """.formatted(topicName);
        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(topicContent))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.topicName").value(topicName));
    }

    @Test
    void createTopic_asStudent_returns403() throws Exception {
        String topicContent = """
            {
                "topicName": "Chương Sinh Viên Không Được Tạo"
            }
            """;
        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(topicContent))
            .andExpect(status().isForbidden());
    }
}
