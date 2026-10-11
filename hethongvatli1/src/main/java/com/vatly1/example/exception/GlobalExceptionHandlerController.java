package com.vatly1.example.exception;

import java.util.Map;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vatly1.example.model.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandlerController {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandlerController.class);

  @Bean
  public ErrorAttributes errorAttributes() {
    return new DefaultErrorAttributes() {
      @Override
      public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        return super.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults().excluding(ErrorAttributeOptions.Include.EXCEPTION));
      }
    };
  }

  /**
   * 1. Xử lý CustomException (Ngoại lệ nghiệp vụ chủ động từ tầng Service)
   */
  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
    return ResponseEntity.status(ex.getHttpStatus())
        .body(ApiResponse.error(ex.getHttpStatus().value(), ex.getMessage()));
  }

  /**
   * 2. Xử lý lỗi Bean Validation trên @RequestBody (@Valid, @NotNull, @NotBlank,...)
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(err -> err.getField() + ": " + err.getDefaultMessage())
        .orElseGet(() -> ex.getBindingResult().getGlobalErrors().stream()
            .findFirst()
            .map(err -> err.getObjectName() + ": " + err.getDefaultMessage())
            .orElse("Dữ liệu yêu cầu không hợp lệ"));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
  }

  /**
   * 3. Xử lý lỗi sai kiểu dữ liệu tham số trên URL (@PathVariable, @RequestParam)
   * Ví dụ: truyền chuỗi không phải UUID vào @PathVariable UUID id, hoặc truyền chữ vào số nguyên/enum.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
    String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "không xác định";
    String message = String.format("Tham số '%s' không đúng định dạng (yêu cầu kiểu %s)", ex.getName(), requiredType);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
  }

  /**
   * 4. Xử lý lỗi JSON body sai cú pháp hoặc không đọc được
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
    log.warn("Malformed JSON or unreadable HTTP message: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Định dạng dữ liệu yêu cầu (JSON) không hợp lệ hoặc thiếu nội dung body"));
  }

  /**
   * 5. Xử lý lỗi thiếu tham số bắt buộc trong Query String (@RequestParam)
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
    String message = String.format("Thiếu tham số bắt buộc: '%s'", ex.getParameterName());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
  }

  /**
   * 6. Xử lý lỗi thiếu phần tệp/dữ liệu trong Multipart request (@RequestPart)
   */
  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestPartException(MissingServletRequestPartException ex) {
    String message = String.format("Thiếu phần dữ liệu tệp tải lên bắt buộc: '%s'", ex.getRequestPartName());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
  }

  /**
   * 7. Xử lý lỗi dung lượng tệp tải lên vượt quá giới hạn cho phép
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(ApiResponse.error(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Kích thước tệp tải lên vượt quá giới hạn cho phép"));
  }

  /**
   * 8. Xử lý lỗi phương thức HTTP không được hỗ trợ (405 Method Not Allowed)
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
    String message = String.format("Phương thức HTTP '%s' không được hỗ trợ cho tài nguyên này", ex.getMethod());
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ApiResponse.error(HttpStatus.METHOD_NOT_ALLOWED.value(), message));
  }

  /**
   * 9. Xử lý lỗi Content-Type không được hỗ trợ (415 Unsupported Media Type)
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(ApiResponse.error(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Định dạng nội dung (Content-Type) không được hỗ trợ"));
  }

  /**
   * 10. Xử lý lỗi định dạng phản hồi không được chấp nhận (406 Not Acceptable)
   */
  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
        .body(ApiResponse.error(HttpStatus.NOT_ACCEPTABLE.value(), "Định dạng phản hồi yêu cầu không được hỗ trợ"));
  }

  /**
   * 11. Xử lý lỗi vi phạm ràng buộc tham số trên Controller (@Validated, @Min, @Size,...)
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
    String message = ex.getConstraintViolations().stream()
        .findFirst()
        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
        .orElse("Dữ liệu tham số không hợp lệ");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
  }

  /**
   * 12. Xử lý lỗi đối số không hợp lệ (IllegalArgumentException)
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
    log.warn("Illegal argument: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage() != null ? ex.getMessage() : "Đối số không hợp lệ"));
  }

  /**
   * 13. Xử lý lỗi vi phạm ràng buộc dữ liệu cơ sở dữ liệu (Unique key, foreign key,...)
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
    log.warn("Database integrity violation: {}", ex.getMessage());
    String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
    if (msg.contains("uq_active_enrollment_subject_semester")) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ApiResponse.error(HttpStatus.CONFLICT.value(), "Sinh viên đã đăng ký vào một lớp khác của môn học này trong cùng học kỳ"));
    }
    if (msg.contains("uq_chapters_subject_code")) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ApiResponse.error(HttpStatus.CONFLICT.value(), "Mã chương đã tồn tại trong môn học này"));
    }
    if (msg.contains("uq_matrix_family_revision")) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ApiResponse.error(HttpStatus.CONFLICT.value(), "Phiên bản ma trận đề thi (revision) đã tồn tại trong nhóm ma trận này"));
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc toàn vẹn cơ sở dữ liệu"));
  }

  /**
   * 13.1. Xử lý xung đột đồng thời Optimistic Locking (@Version)
   */
  @ExceptionHandler({
      org.springframework.orm.ObjectOptimisticLockingFailureException.class,
      jakarta.persistence.OptimisticLockException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLockException(Exception ex) {
    log.warn("Optimistic locking conflict: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(HttpStatus.CONFLICT.value(), "Dữ liệu đã bị thay đổi bởi thao tác khác cùng thời điểm. Vui lòng tải lại và thử lại."));
  }

  /**
   * 14. Xử lý lỗi phân quyền (403 Forbidden - Access Denied)
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied"));
  }

  /**
   * 15. Xử lý lỗi xác thực người dùng (401 Unauthorized)
   */
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "Tên đăng nhập hoặc mật khẩu không chính xác"));
  }

  /**
   * 16. Xử lý lỗi không tìm thấy tài nguyên / đường dẫn (404 Not Found)
   */
  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ApiResponse<Void>> handleNotFoundException(Exception ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "Tài nguyên không tồn tại: " + ex.getMessage()));
  }

  /**
   * 17. Xử lý lỗi hệ thống chưa được phân loại (500 Internal Server Error)
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Something went wrong"));
  }

}