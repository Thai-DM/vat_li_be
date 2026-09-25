package com.vatly1.example.controller;

import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.dto.EvidenceDTO;
import com.vatly1.example.service.IEvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Evidence Management", description = "APIs Nộp minh chứng kết quả đo thí nghiệm ảo, chấm điểm & xác nhận kết quả")
@SecurityRequirement(name = "bearerAuth")
public class EvidenceController {

    private final IEvidenceService evidenceService;

    @Operation(summary = "Lấy kho minh chứng thí nghiệm của sinh viên hiện tại", description = "Trả về danh sách kết quả đo đạc, báo cáo thí nghiệm ảo của sinh viên đang đăng nhập.")
    @GetMapping("/students/me/evidence")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceDTO>>> getMyEvidence(HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        List<EvidenceDTO> data = evidenceService.getMyEvidence(currentUserId);
        return ResponseEntity.ok(ApiResponse.<List<EvidenceDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }

    @Operation(summary = "Lấy kho minh chứng của một sinh viên (Có kiểm tra bảo mật IDOR)", description = "Giảng viên xem minh chứng của sinh viên trong lớp hoặc sinh viên tự xem của mình.")
    @GetMapping("/students/{id}/evidence")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<EvidenceDTO>>> getStudentEvidence(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        List<EvidenceDTO> data = evidenceService.getStudentEvidence(id, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<EvidenceDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }

    @Operation(summary = "Lấy tổng hợp kho minh chứng thí nghiệm của cả lớp", description = "Giảng viên / Quản trị viên xem toàn bộ minh chứng thực hành thí nghiệm ảo của lớp học.")
    @GetMapping("/classes/{id}/evidence")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceDTO>>> getClassEvidence(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        List<EvidenceDTO> data = evidenceService.getClassEvidence(id, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<EvidenceDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }
}