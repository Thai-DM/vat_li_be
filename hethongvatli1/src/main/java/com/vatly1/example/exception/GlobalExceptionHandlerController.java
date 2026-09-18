package com.vatly1.example.exception;

import java.util.Map;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vatly1.example.dto.ApiResponse;

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

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
    return ResponseEntity.status(ex.getHttpStatus()).body(ApiResponse.error(ex.getHttpStatus().value(), ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(err -> err.getField() + ": " + err.getDefaultMessage())
        .orElse("Validation failed");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
    log.warn("Database integrity violation: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc toàn vẹn cơ sở dữ liệu"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied"));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Something went wrong"));
  }

}
