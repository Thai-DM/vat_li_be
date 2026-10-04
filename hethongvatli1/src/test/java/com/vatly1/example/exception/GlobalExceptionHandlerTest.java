package com.vatly1.example.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.vatly1.example.app.JwtAuthServiceApp;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/test-exceptions")
    static class ExceptionTriggerController {

        @GetMapping("/type-mismatch/{id}")
        public String typeMismatch(@PathVariable UUID id) {
            return id.toString();
        }

        @PostMapping("/not-readable")
        public String notReadable(@RequestBody ValidDto body) {
            return body.getName();
        }

        @GetMapping("/missing-param")
        public String missingParam(@RequestParam("keyword") String keyword) {
            return keyword;
        }

        @PostMapping(value = "/missing-part", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public String missingPart(@RequestPart("file") MultipartFile file) {
            return file.getOriginalFilename();
        }

        @GetMapping("/max-upload")
        public String maxUpload() {
            throw new MaxUploadSizeExceededException(1024L);
        }

        @GetMapping("/illegal-arg")
        public String illegalArg() {
            throw new IllegalArgumentException("Tham số không hợp lệ trong logic nghiệp vụ");
        }

        @GetMapping("/constraint-violation")
        public String constraintViolation() {
            throw new ConstraintViolationException("Ràng buộc bị vi phạm", Collections.emptySet());
        }

        @GetMapping("/auth-error")
        public String authError() {
            throw new BadCredentialsException("Sai thông tin đăng nhập");
        }

        @GetMapping("/custom-404")
        public String customNotFound() {
            throw new CustomException("Tài nguyên không tìm thấy", HttpStatus.NOT_FOUND);
        }

        @GetMapping("/unhandled-500")
        public String unhandledError() {
            throw new RuntimeException("Unexpected internal failure");
        }

        @PostMapping("/validation-error")
        public String validationError(@Valid @RequestBody ValidDto body) {
            return body.getName();
        }
    }

    @Data
    static class ValidDto {
        @NotBlank(message = "Tên không được để trống")
        private String name;
    }

    @Test
    @DisplayName("EX-01: MethodArgumentTypeMismatchException -> 400 Bad Request kèm message rõ ràng")
    void testMethodArgumentTypeMismatch() throws Exception {
        mockMvc.perform(get("/test-exceptions/type-mismatch/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Tham số 'id' không đúng định dạng (yêu cầu kiểu UUID)"));
    }

    @Test
    @DisplayName("EX-02: HttpMessageNotReadableException (JSON sai cú pháp) -> 400 Bad Request")
    void testHttpMessageNotReadable() throws Exception {
        mockMvc.perform(post("/test-exceptions/not-readable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": invalid_json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Định dạng dữ liệu yêu cầu (JSON) không hợp lệ hoặc thiếu nội dung body"));
    }

    @Test
    @DisplayName("EX-03: MissingServletRequestParameterException -> 400 Bad Request")
    void testMissingServletRequestParameter() throws Exception {
        mockMvc.perform(get("/test-exceptions/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Thiếu tham số bắt buộc: 'keyword'"));
    }

    @Test
    @DisplayName("EX-04: MissingServletRequestPartException -> 400 Bad Request")
    void testMissingServletRequestPart() throws Exception {
        mockMvc.perform(multipart("/test-exceptions/missing-part")
                        .file(new MockMultipartFile("wrongPartName", "test.txt", "text/plain", "data".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Thiếu phần dữ liệu tệp tải lên bắt buộc: 'file'"));
    }

    @Test
    @DisplayName("EX-05: MaxUploadSizeExceededException -> 413 Payload Too Large")
    void testMaxUploadSizeExceeded() throws Exception {
        mockMvc.perform(get("/test-exceptions/max-upload"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value("Kích thước tệp tải lên vượt quá giới hạn cho phép"));
    }

    @Test
    @DisplayName("EX-06: HttpRequestMethodNotSupportedException (POST gọi vào endpoint GET) -> 405 Method Not Allowed")
    void testHttpRequestMethodNotSupported() throws Exception {
        mockMvc.perform(post("/test-exceptions/illegal-arg"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("Phương thức HTTP 'POST' không được hỗ trợ cho tài nguyên này"));
    }

    @Test
    @DisplayName("EX-07: IllegalArgumentException -> 400 Bad Request")
    void testIllegalArgumentException() throws Exception {
        mockMvc.perform(get("/test-exceptions/illegal-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Tham số không hợp lệ trong logic nghiệp vụ"));
    }

    @Test
    @DisplayName("EX-08: AuthenticationException -> 401 Unauthorized")
    void testAuthenticationException() throws Exception {
        mockMvc.perform(get("/test-exceptions/auth-error"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"));
    }

    @Test
    @DisplayName("EX-09: CustomException (404) -> 404 Not Found")
    void testCustomExceptionNotFound() throws Exception {
        mockMvc.perform(get("/test-exceptions/custom-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Tài nguyên không tìm thấy"));
    }

    @Test
    @DisplayName("EX-10: MethodArgumentNotValidException (@Valid fail) -> 400 Bad Request")
    void testValidationException() throws Exception {
        mockMvc.perform(post("/test-exceptions/validation-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("name: Tên không được để trống"));
    }

    @Test
    @DisplayName("EX-11: Fallback Unhandled Exception -> 500 Internal Server Error")
    void testUnhandledException() throws Exception {
        mockMvc.perform(get("/test-exceptions/unhandled-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Something went wrong"));
    }
}
