package com.vatly1.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vatly1.example.app.JwtAuthServiceApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorHealthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("HEALTH-01: Anonymous GET /actuator/health -> 200 OK với status UP")
    void testHEALTH01_AnonymousHealthCheck_ReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("HEALTH-02: Anonymous GET /actuator/metrics -> 403 Forbidden hoặc 401 Unauthorized (bị chặn)")
    void testHEALTH02_AnonymousMetrics_IsProtected() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }
}
