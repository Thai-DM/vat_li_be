package com.vatly1.example.controller;

import com.vatly1.example.dto.ApiResponse;
import com.vatly1.example.dto.BulkUpdateSettingsRequest;
import com.vatly1.example.dto.UpdateSettingRequest;
import com.vatly1.example.entity.SystemSetting;
import com.vatly1.example.service.ISystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
@Tag(name = "System Settings", description = "APIs Cấu hình tham số động hệ thống (Dynamic Configuration)")
@SecurityRequirement(name = "bearerAuth")
public class SystemSettingController {

    private final ISystemSettingService settingService;

    @Operation(summary = "Lấy toàn bộ cấu hình hệ thống", description = "Chỉ Admin được quyền truy cập.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemSetting>>> getAllSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingService.getAllSettings()));
    }

    @Operation(summary = "Lấy một cấu hình theo key", description = "Chỉ Admin được quyền truy cập.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemSetting>> getSettingByKey(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.success(settingService.getSettingByKey(key)));
    }

    @Operation(summary = "Cập nhật một cấu hình", description = "Chỉ Admin được quyền truy cập.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemSetting>> updateSetting(
            @PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request,
            HttpServletRequest httpServletRequest) {
        UUID adminUserId = getUserIdFromRequest(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(settingService.updateSetting(key, request, adminUserId)));
    }

    @Operation(summary = "Cập nhật nhiều cấu hình cùng lúc", description = "Chỉ Admin được quyền truy cập.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<SystemSetting>>> bulkUpdateSettings(
            @Valid @RequestBody BulkUpdateSettingsRequest request,
            HttpServletRequest httpServletRequest) {
        UUID adminUserId = getUserIdFromRequest(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(settingService.bulkUpdateSettings(request, adminUserId)));
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj != null) {
            try {
                return UUID.fromString(userIdObj.toString());
            } catch (Exception ignored) {}
        }
        return null;
    }
}
