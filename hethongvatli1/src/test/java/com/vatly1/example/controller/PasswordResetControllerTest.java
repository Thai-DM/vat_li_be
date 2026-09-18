package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.dto.ForgotPasswordRequestDTO;
import com.vatly1.example.dto.ResetPasswordRequestDTO;
import com.vatly1.example.dto.SigninRequestDTO;
import com.vatly1.example.entity.PasswordResetToken;
import com.vatly1.example.entity.User;
import com.vatly1.example.filter.RateLimitFilter;
import com.vatly1.example.repository.IPasswordResetTokenRepository;
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

import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IPasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        RateLimitFilter.reset();
        User svBinh = userRepository.findByUsername("sv_binh");
        if (svBinh != null) {
            svBinh.setPasswordHash(passwordEncoder.encode("sv_binh123456"));
            userRepository.save(svBinh);
        }
    }

    @Test
    @DisplayName("PWD-01: Yêu cầu quên mật khẩu với email hợp lệ -> Tạo token 15 phút và trả về 200 OK")
    void testPWD01_ForgotPassword_Success_GeneratesToken() throws Exception {
        ForgotPasswordRequestDTO request = ForgotPasswordRequestDTO.builder()
                .email("sv_an@email.com")
                .build();

        mockMvc.perform(post("/api/v1/users/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Nếu email tồn tại trên hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."));

        User user = userRepository.findByUsername("sv_an");
        assertNotNull(user);

        // Verify token created
        PasswordResetToken token = tokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(user.getUserId()) && !t.isUsed())
                .findFirst()
                .orElse(null);

        assertNotNull(token);
        assertEquals("sv_an@email.com", token.getEmail());
        assertFalse(token.isUsed());
        assertTrue(token.getExpiryDate().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("PWD-02: Yêu cầu quên mật khẩu với email không tồn tại -> Trả về 200 OK thông báo an toàn, không lộ thông tin")
    void testPWD02_ForgotPassword_EmailNotFound_ReturnsSafeMessage() throws Exception {
        ForgotPasswordRequestDTO request = ForgotPasswordRequestDTO.builder()
                .email("nonexistent_user_999@email.com")
                .build();

        mockMvc.perform(post("/api/v1/users/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Nếu email tồn tại trên hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."));
    }

    @Test
    @DisplayName("PWD-03: Yêu cầu quên mật khẩu với định dạng email sai -> Trả về 400 Bad Request")
    void testPWD03_ForgotPassword_InvalidEmail_Returns400() throws Exception {
        ForgotPasswordRequestDTO request = ForgotPasswordRequestDTO.builder()
                .email("invalid-email-string")
                .build();

        mockMvc.perform(post("/api/v1/users/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PWD-04: Đặt lại mật khẩu với token hợp lệ -> Thành công và đăng nhập được bằng mật khẩu mới")
    void testPWD04_ResetPassword_Success_CanLoginWithNewPassword() throws Exception {
        String testUsername = "sv_pwd_test_user";
        User user = userRepository.findByUsername(testUsername);
        if (user == null) {
            user = userRepository.save(User.builder()
                    .username(testUsername)
                    .passwordHash(passwordEncoder.encode("initialPass123"))
                    .email("sv_pwd_test_user@email.com")
                    .role(UserRole.STUDENT)
                    .status(UserStatus.ACTIVE)
                    .build());
        }
        assertNotNull(user);

        String testToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(testToken)
                .userId(user.getUserId())
                .email(user.getEmail())
                .expiryDate(Instant.now().plus(15, ChronoUnit.MINUTES))
                .used(false)
                .build();
        tokenRepository.save(resetToken);

        String newPassword = "newStrongPassword2026";
        ResetPasswordRequestDTO resetRequest = ResetPasswordRequestDTO.builder()
                .token(testToken)
                .newPassword(newPassword)
                .build();

        mockMvc.perform(post("/api/v1/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập với mật khẩu mới."));

        // Verify token is now marked as used
        PasswordResetToken updatedToken = tokenRepository.findByToken(testToken).orElseThrow();
        assertTrue(updatedToken.isUsed());

        // Verify student can now sign in with the new password
        SigninRequestDTO signinRequest = new SigninRequestDTO(testUsername, newPassword);

        mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signinRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("PWD-05: Đặt lại mật khẩu với token đã qua sử dụng -> Bị từ chối 400 Bad Request")
    void testPWD05_ResetPassword_UsedToken_Returns400() throws Exception {
        User user = userRepository.findByUsername("sv_an");
        assertNotNull(user);

        String testToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(testToken)
                .userId(user.getUserId())
                .email(user.getEmail())
                .expiryDate(Instant.now().plus(15, ChronoUnit.MINUTES))
                .used(true) // already used
                .build();
        tokenRepository.save(resetToken);

        ResetPasswordRequestDTO resetRequest = ResetPasswordRequestDTO.builder()
                .token(testToken)
                .newPassword("anotherPassword123")
                .build();

        mockMvc.perform(post("/api/v1/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã token này đã được sử dụng"));
    }

    @Test
    @DisplayName("PWD-06: Đặt lại mật khẩu với token đã hết hạn -> Bị từ chối 400 Bad Request")
    void testPWD06_ResetPassword_ExpiredToken_Returns400() throws Exception {
        User user = userRepository.findByUsername("sv_an");
        assertNotNull(user);

        String testToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(testToken)
                .userId(user.getUserId())
                .email(user.getEmail())
                .expiryDate(Instant.now().minus(5, ChronoUnit.MINUTES)) // expired 5 mins ago
                .used(false)
                .build();
        tokenRepository.save(resetToken);

        ResetPasswordRequestDTO resetRequest = ResetPasswordRequestDTO.builder()
                .token(testToken)
                .newPassword("anotherPassword123")
                .build();

        mockMvc.perform(post("/api/v1/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã token đã hết hạn. Vui lòng gửi lại yêu cầu mới"));
    }

    @Test
    @DisplayName("PWD-07: Đặt lại mật khẩu với token không tồn tại -> Bị từ chối 400 Bad Request")
    void testPWD07_ResetPassword_InvalidToken_Returns400() throws Exception {
        ResetPasswordRequestDTO resetRequest = ResetPasswordRequestDTO.builder()
                .token("completely-non-existent-token")
                .newPassword("anotherPassword123")
                .build();

        mockMvc.perform(post("/api/v1/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã token không hợp lệ hoặc không tồn tại"));
    }

    @Test
    @DisplayName("PWD-08: Gửi /forgot-password liên tiếp quá 3 lần/phút/IP -> Lần 4 bị chặn 429 Too Many Requests")
    void testPWD08_ForgotPassword_RateLimit_BlocksOn4thRequest() throws Exception {
        String testIp = "192.168.100.50";
        ForgotPasswordRequestDTO request = ForgotPasswordRequestDTO.builder()
                .email("sv_an@email.com")
                .build();

        // 3 requests allowed
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/users/forgot-password")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // 4th request must be 429
        mockMvc.perform(post("/api/v1/users/forgot-password")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }
}
