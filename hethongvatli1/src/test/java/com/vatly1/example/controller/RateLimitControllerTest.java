package com.vatly1.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.filter.RateLimitFilter;
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
class RateLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RateLimitFilter.reset();
    }

    @Test
    @DisplayName("RATE-01: Gọi /signin 10 lần liên tiếp -> lần thứ 11 trả 429 Too Many Requests")
    void testRATE01_SigninRateLimit_BlocksOn11thRequest() throws Exception {
        String signinBody = "{\"username\":\"admin\",\"password\":\"wrongpassword\"}";
        String testIp = "10.0.0.1";

        // 10 first requests: not 429
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/users/signin")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signinBody))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        if (status == 429) {
                            throw new AssertionError("Request " + status + " was unexpectedly rate limited too early");
                        }
                    });
        }

        // 11th request: must be 429 Too Many Requests
        mockMvc.perform(post("/api/v1/users/signin")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    @DisplayName("RATE-02: Gọi /signup 3 lần liên tiếp -> lần thứ 4 trả 429 Too Many Requests")
    void testRATE02_SignupRateLimit_BlocksOn4thRequest() throws Exception {
        String uniqueUsername = "rl_user_" + System.currentTimeMillis();
        String signupBody = "{\"username\":\"" + uniqueUsername + "\",\"password\":\"password123\",\"email\":\"test@test.com\"}";
        String testIp = "10.0.0.2";

        // 3 first requests: not 429
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/users/signup")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        if (status == 429) {
                            throw new AssertionError("Request was unexpectedly rate limited too early");
                        }
                    });
        }

        // 4th request: must be 429 Too Many Requests
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }
}