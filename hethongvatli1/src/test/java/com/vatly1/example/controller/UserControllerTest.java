package com.vatly1.example.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.User;
import com.vatly1.example.repository.IUserRepository;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

  private static final String ADMIN_USER = "admin";
  private static final String ADMIN_PASSWORD = "admin123456";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private IUserRepository userRepository;

  private String signinAs(String username, String password) throws Exception {
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

  private JsonNode signin() throws Exception {
    String bodyContent = "{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}";
    String body = mockMvc.perform(post("/api/v1/users/signin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(bodyContent))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    return objectMapper.readTree(body);
  }

  private String refreshBody(String refreshToken) throws Exception {
    return objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken));
  }

  @Test
  void signin_withValidCredentials_returnsTokenPair() throws Exception {
    JsonNode tokens = signin();
    assertTrue(tokens.get("data").get("accessToken").asText().length() > 20, "Expected a JWT access token");
    assertTrue(tokens.get("data").get("refreshToken").asText().length() > 20, "Expected a refresh token");
    assertTrue(tokens.get("data").get("expiresIn").asLong() > 0, "Expected a positive access token lifetime");
  }

  @Test
  void me_withoutToken_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isForbidden());
  }

  @Test
  void me_withValidToken_returnsUserData() throws Exception {
    String accessToken = signin().get("data").get("accessToken").asText();

    mockMvc.perform(get("/api/v1/users/me")
            .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value(ADMIN_USER))
        .andExpect(jsonPath("$.data.email").value("admin@email.com"))
        .andExpect(jsonPath("$.data.role").value("ADMIN"));
  }

  @Test
  void refresh_withValidRefreshToken_returnsNewPair() throws Exception {
    JsonNode tokens = signin();
    String refreshToken = tokens.get("data").get("refreshToken").asText();

    String body = mockMvc.perform(post("/api/v1/users/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody(refreshToken)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    JsonNode refreshed = objectMapper.readTree(body);
    assertTrue(refreshed.get("data").get("accessToken").asText().length() > 20, "Expected a new access token");
    assertNotEquals(refreshToken, refreshed.get("data").get("refreshToken").asText(), "Refresh token should rotate");

    mockMvc.perform(get("/api/v1/users/me")
            .header("Authorization", "Bearer " + refreshed.get("data").get("accessToken").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value(ADMIN_USER));
  }

  @Test
  void refresh_worksWithoutAccessToken() throws Exception {
    String refreshToken = signin().get("data").get("refreshToken").asText();

    mockMvc.perform(post("/api/v1/users/refresh")
            .header("Authorization", "Bearer expired-and-invalid")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody(refreshToken)))
        .andExpect(status().isOk());
  }

  @Test
  void refresh_reusingRotatedToken_returns401() throws Exception {
    String refreshToken = signin().get("data").get("refreshToken").asText();

    mockMvc.perform(post("/api/v1/users/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody(refreshToken)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/users/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody(refreshToken)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refresh_afterReuseDetection_revokesRemainingTokens() throws Exception {
    String refreshToken = signin().get("data").get("refreshToken").asText();

    String body = mockMvc.perform(post("/api/v1/users/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody(refreshToken)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    String rotated = objectMapper.readTree(body).get("data").get("refreshToken").asText();

    mockMvc.perform(post("/api/v1/users/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody(refreshToken)))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/api/v1/users/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody(rotated)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refresh_withUnknownToken_returns401() throws Exception {
    mockMvc.perform(post("/api/v1/users/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody("not-a-real-refresh-token")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refresh_withBlankToken_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/users/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody("")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void logout_revokesRefreshToken() throws Exception {
    JsonNode tokens = signin();
    String accessToken = tokens.get("data").get("accessToken").asText();
    String refreshToken = tokens.get("data").get("refreshToken").asText();

    mockMvc.perform(post("/api/v1/users/logout")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody(refreshToken)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/users/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody(refreshToken)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logout_withUnknownToken_isIdempotent() throws Exception {
    String accessToken = signin().get("data").get("accessToken").asText();
    
    mockMvc.perform(post("/api/v1/users/logout")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshBody("never-issued")))
        .andExpect(status().isOk());
  }

  @Test
  void signup_returnsTokenPair() throws Exception {
    String body = """
        {"username":"newuser","email":"newuser@example.com","password":"password12","role":"STUDENT"}
        """;
    String response = mockMvc.perform(post("/api/v1/users/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    JsonNode tokens = objectMapper.readTree(response);
    assertTrue(tokens.get("data").get("accessToken").asText().length() > 20, "Expected a JWT access token");
    assertTrue(tokens.get("data").get("refreshToken").asText().length() > 20, "Expected a refresh token");
  }

  @Test
  void signup_duplicateUsername_returns422() throws Exception {
    String body = """
        {"username":"admin","email":"other@example.com","password":"password12","role":"STUDENT"}
        """;
    mockMvc.perform(post("/api/v1/users/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void me_withMalformedToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/users/me")
            .header("Authorization", "Bearer not-a-valid-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @org.junit.jupiter.api.DisplayName("REFRESH-CONCURRENCY: 2 request refresh cùng lúc với cùng 1 token -> chỉ 1 request thành công")
  void concurrentRefresh_withSameToken_onlyOneSucceeds() throws Exception {
    String refreshToken = signin().get("data").get("refreshToken").asText();

    int threads = 2;
    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
    java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threads);
    java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
    java.util.concurrent.atomic.AtomicInteger failureCount = new java.util.concurrent.atomic.AtomicInteger(0);

    java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();

    for (int i = 0; i < threads; i++) {
      futures.add(executor.submit(() -> {
        readyLatch.countDown();
        try {
          startLatch.await();
          int status = mockMvc.perform(post("/api/v1/users/refresh")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(refreshBody(refreshToken)))
              .andReturn()
              .getResponse()
              .getStatus();
          if (status == 200) {
            successCount.incrementAndGet();
          } else {
            failureCount.incrementAndGet();
          }
        } catch (Exception e) {
          failureCount.incrementAndGet();
        }
      }));
    }

    readyLatch.await();
    startLatch.countDown();

    for (java.util.concurrent.Future<?> future : futures) {
      future.get();
    }
    executor.shutdown();

    org.junit.jupiter.api.Assertions.assertEquals(1, successCount.get(), "Chỉ đúng 1 request refresh được cấp token mới thành công");
    org.junit.jupiter.api.Assertions.assertEquals(1, failureCount.get(), "Request đồng thời còn lại phải bị từ chối");
  }

  @Test
  @DisplayName("USR-13: PUT /api/v1/users/me cập nhật email thành công")
  void updateUserMe_validEmail_success() throws Exception {
    String token = signinAs("sv_an", "sv_an123456");
    String updateBody = "{\"email\":\"sv_an_updated@example.com\"}";

    mockMvc.perform(put("/api/v1/users/me")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("sv_an_updated@example.com"));
  }

  @Test
  @DisplayName("USR-14: PUT /api/v1/users/me đổi username đã tồn tại -> 422")
  void updateUserMe_duplicateUsername_returns422() throws Exception {
    String token = signinAs("sv_binh", "sv_binh123456");
    String updateBody = "{\"username\":\"admin\"}";

    mockMvc.perform(put("/api/v1/users/me")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateBody))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("USR-15: PUT /api/v1/users/me/password đúng mật khẩu cũ -> đổi thành công và đăng nhập được")
  void changePassword_validOldPassword_success() throws Exception {
    String uniqueUser = "pwuser_" + System.currentTimeMillis();
    String signupBody = String.format("{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"oldPass123\",\"role\":\"STUDENT\"}", uniqueUser, uniqueUser);
    mockMvc.perform(post("/api/v1/users/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupBody))
        .andExpect(status().isOk());

    String token = signinAs(uniqueUser, "oldPass123");
    String changeBody = "{\"oldPassword\":\"oldPass123\",\"newPassword\":\"newPass456\"}";

    mockMvc.perform(put("/api/v1/users/me/password")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(changeBody))
        .andExpect(status().isOk());

    signinAs(uniqueUser, "newPass456");
  }

  @Test
  @DisplayName("USR-16: PUT /api/v1/users/me/password sai mật khẩu cũ -> 400 Bad Request")
  void changePassword_invalidOldPassword_returns400() throws Exception {
    String token = signinAs("sv_cuong", "sv_cuong123456");
    String changeBody = "{\"oldPassword\":\"wrongPassword\",\"newPassword\":\"newPass456\"}";

    mockMvc.perform(put("/api/v1/users/me/password")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(changeBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("USR-17: GET /api/v1/users/me/profile trả về hồ sơ cá nhân")
  void getMyProfile_success() throws Exception {
    String token = signinAs("admin", "admin123456");

    mockMvc.perform(get("/api/v1/users/me/profile")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").exists());
  }

  @Test
  @DisplayName("USR-18: PUT /api/v1/users/me/profile cập nhật thông tin hồ sơ")
  void updateMyProfile_success() throws Exception {
    String token = signinAs("gv_nguyen", "gv_nguyen123456");
    String profileBody = """
        {
          "fullName": "Nguyễn Văn Giảng Viên Cập Nhật",
          "phone": "0987654321",
          "gender": "MALE",
          "dateOfBirth": "1985-05-15",
          "bio": "Giảng viên bộ môn Vật Lý"
        }
        """;

    mockMvc.perform(put("/api/v1/users/me/profile")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(profileBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn Giảng Viên Cập Nhật"))
        .andExpect(jsonPath("$.data.phone").value("0987654321"))
        .andExpect(jsonPath("$.data.bio").value("Giảng viên bộ môn Vật Lý"));
  }

  @Test
  @DisplayName("USR-19: GET /api/v1/users/admin/users Admin lấy danh sách user phân trang")
  void getAllUsers_asAdmin_success() throws Exception {
    String adminToken = signinAs("admin", "admin123456");

    mockMvc.perform(get("/api/v1/users/admin/users?page=0&size=5")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0]").exists());
  }

  @Test
  @DisplayName("USR-20: GET /api/v1/users/admin/users Non-admin (Student) -> 403 Forbidden")
  void getAllUsers_asStudent_returns403() throws Exception {
    String studentToken = signinAs("sv_an", "sv_an123456");

    mockMvc.perform(get("/api/v1/users/admin/users")
            .header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("USR-21: GET /api/v1/users/admin/users/{id}/profile Admin xem profile user bất kỳ")
  void getUserProfile_asAdmin_success() throws Exception {
    String adminToken = signinAs("admin", "admin123456");
    User sv = userRepository.findByUsername("sv_an");

    mockMvc.perform(get("/api/v1/users/admin/users/" + sv.getUserId() + "/profile")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").exists());
  }

  @Test
  @DisplayName("USR-22: PUT /api/v1/users/admin/users/{id} Admin cập nhật role user")
  void adminUpdateUser_changeRole_success() throws Exception {
    String adminToken = signinAs("admin", "admin123456");
    User ta = userRepository.findByUsername("ta_hung");

    String updateBody = "{\"role\":\"INSTRUCTOR\"}";

    mockMvc.perform(put("/api/v1/users/admin/users/" + ta.getUserId())
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("INSTRUCTOR"));
  }

  @Test
  @DisplayName("USR-23: PUT /api/v1/users/admin/users/{id}/status Admin khóa tài khoản (LOCKED)")
  void adminUpdateUserStatus_lockUser_success() throws Exception {
    String adminToken = signinAs("admin", "admin123456");
    User sv = userRepository.findByUsername("sv_cuong");

    String statusBody = "{\"status\":\"LOCKED\"}";

    mockMvc.perform(put("/api/v1/users/admin/users/" + sv.getUserId() + "/status")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(statusBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("LOCKED"));
  }

  @Test
  @DisplayName("USR-24: PUT /api/v1/users/admin/users/{id}/status Admin mở khóa tài khoản (ACTIVE)")
  void adminUpdateUserStatus_unlockUser_success() throws Exception {
    String adminToken = signinAs("admin", "admin123456");
    User sv = userRepository.findByUsername("sv_cuong");

    String statusBody = "{\"status\":\"ACTIVE\"}";

    mockMvc.perform(put("/api/v1/users/admin/users/" + sv.getUserId() + "/status")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(statusBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }
}