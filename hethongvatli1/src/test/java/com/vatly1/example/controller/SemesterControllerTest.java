package com.vatly1.example.controller;

import static org.hamcrest.Matchers.greaterThan;
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
class SemesterControllerTest {

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
    void getAllSemesters_returnsList() throws Exception {
        mockMvc.perform(get("/api/v1/semesters")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(greaterThan(0))));
    }

    @Test
    void getSemesterById_returnsSemester() throws Exception {
        // First get all to find an ID
        String body = mockMvc.perform(get("/api/v1/semesters")
                .header("Authorization", "Bearer " + adminToken))
            .andReturn().getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(body);
        String semesterId = jsonNode.get("data").get(0).get("semesterId").asText();

        mockMvc.perform(get("/api/v1/semesters/" + semesterId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.semesterId").value(semesterId));
    }

    @Test
    void createSemester_asAdmin_success() throws Exception {
        String newSemesterCode = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        String bodyContent = """
            {
                "semesterCode": "%s",
                "semesterName": "Học kỳ Test",
                "academicYear": "2026-2027",
                "startDate": "2026-09-01",
                "endDate": "2027-01-15"
            }
            """.formatted(newSemesterCode);

        mockMvc.perform(post("/api/v1/semesters")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyContent))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.semesterCode").value(newSemesterCode));
    }

    @Test
    void createSemester_asStudent_returns403() throws Exception {
        String bodyContent = """
            {
                "semesterCode": "HK1_2027_2028",
                "semesterName": "Học kỳ 1",
                "academicYear": "2027-2028",
                "startDate": "2027-09-01",
                "endDate": "2028-01-15"
            }
            """;

        mockMvc.perform(post("/api/v1/semesters")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyContent))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateSemester_asAdmin_success() throws Exception {
        // Create one first to update
        String newSemesterCode = "TEST_UPD_" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
                "semesterCode": "%s",
                "semesterName": "To Update",
                "academicYear": "2026-2027",
                "startDate": "2026-09-01",
                "endDate": "2027-01-15"
            }
            """.formatted(newSemesterCode);

        String createRes = mockMvc.perform(post("/api/v1/semesters")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andReturn().getResponse().getContentAsString();
            
        String semesterId = objectMapper.readTree(createRes).get("data").get("semesterId").asText();

        String updateBody = """
            {
                "semesterName": "Updated Name",
                "academicYear": "2026-2027",
                "startDate": "2026-09-02",
                "endDate": "2027-01-16"
            }
            """;

        mockMvc.perform(put("/api/v1/semesters/" + semesterId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.semesterName").value("Updated Name"));
    }

    @Test
    void setCurrentSemester_asAdmin_success() throws Exception {
        // Create one first
        String newSemesterCode = "TEST_CUR_" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
            {
                "semesterCode": "%s",
                "semesterName": "To Current",
                "academicYear": "2026-2027",
                "startDate": "2026-09-01",
                "endDate": "2027-01-15"
            }
            """.formatted(newSemesterCode);

        String createRes = mockMvc.perform(post("/api/v1/semesters")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andReturn().getResponse().getContentAsString();
            
        String semesterId = objectMapper.readTree(createRes).get("data").get("semesterId").asText();

        mockMvc.perform(put("/api/v1/semesters/" + semesterId + "/set-current")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isCurrent").value(true));
    }

    @Autowired
    private com.vatly1.example.repository.ISemesterRepository semesterRepository;

    @Test
    @org.junit.jupiter.api.DisplayName("SEMESTER-CONCURRENCY: 2 request set-current đồng thời cho 2 kỳ khác nhau -> chỉ đúng 1 kỳ isCurrent = true")
    void concurrentSetCurrentSemester_leavesExactlyOneCurrentSemester() throws Exception {
        // Tạo 2 học kỳ A và B
        String semCodeA = "CONC_A_" + UUID.randomUUID().toString().substring(0, 8);
        String semCodeB = "CONC_B_" + UUID.randomUUID().toString().substring(0, 8);

        String createA = """
            {
                "semesterCode": "%s",
                "semesterName": "Kỳ A %s",
                "academicYear": "2030-2031",
                "startDate": "2030-09-01",
                "endDate": "2031-01-15"
            }
            """.formatted(semCodeA, UUID.randomUUID());
        String resA = mockMvc.perform(post("/api/v1/semesters")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createA))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String idA = objectMapper.readTree(resA).get("data").get("semesterId").asText();

        String createB = """
            {
                "semesterCode": "%s",
                "semesterName": "Kỳ B %s",
                "academicYear": "2031-2032",
                "startDate": "2031-09-01",
                "endDate": "2032-01-15"
            }
            """.formatted(semCodeB, UUID.randomUUID());
        String resB = mockMvc.perform(post("/api/v1/semesters")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createB))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String idB = objectMapper.readTree(resB).get("data").get("semesterId").asText();

        // Chạy 2 luồng đồng thời gọi set-current
        int threads = 2;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);

        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();

        futures.add(executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                mockMvc.perform(put("/api/v1/semesters/" + idA + "/set-current")
                        .header("Authorization", "Bearer " + adminToken));
            } catch (Exception ignored) {}
        }));

        futures.add(executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                mockMvc.perform(put("/api/v1/semesters/" + idB + "/set-current")
                        .header("Authorization", "Bearer " + adminToken));
            } catch (Exception ignored) {}
        }));

        readyLatch.await();
        startLatch.countDown();

        for (java.util.concurrent.Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Kiểm tra cơ sở dữ liệu: Phải có đúng 1 học kỳ có isCurrent = true
        long currentSemestersCount = semesterRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsCurrent()))
                .count();

        org.junit.jupiter.api.Assertions.assertEquals(1, currentSemestersCount,
                "Toàn hệ thống chỉ được phép có đúng 1 học kỳ hiện tại (isCurrent = true)");
    }
}