package com.vatly1.example.controller;

import com.vatly1.example.model.dto.ClassDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.IClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
@Tag(name = "Student Class & Enrollment", description = "APIs Đăng ký vào lớp, phê duyệt sinh viên tham gia học phần")
@SecurityRequirement(name = "bearerAuth")
public class StudentClassController {

    private final IClassService classService;

    @Operation(summary = "Lấy danh sách lớp học của tôi", description = "Lấy danh sách các lớp học mà sinh viên đang ghi danh.")
    @GetMapping("/classes")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Page<ClassDTO>>> getMyClasses(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        return ResponseEntity.ok(ApiResponse.success(classService.getMyClasses(currentUserId, page, size)));
    }
}