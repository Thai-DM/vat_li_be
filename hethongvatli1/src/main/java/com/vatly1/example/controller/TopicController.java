package com.vatly1.example.controller;

import com.vatly1.example.dto.ApiResponse;
import com.vatly1.example.dto.CreateTopicDTO;
import com.vatly1.example.dto.TopicDTO;
import com.vatly1.example.service.ITopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subjects/{subjectId}/topics")
@RequiredArgsConstructor
@Tag(name = "Topic Management", description = "APIs Quản lý chương mục kiến thức Vật lý 1 (Cơ học, Động học, Năng lượng, Va chạm...)")
@SecurityRequirement(name = "bearerAuth")
public class TopicController {

    private final ITopicService topicService;

    @Operation(summary = "Lấy danh sách chương mục theo môn học", description = "Trả về toàn bộ các chương/chủ đề kiến thức thuộc môn học.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TopicDTO>>> getTopicsBySubject(@PathVariable UUID subjectId) {
        List<TopicDTO> topics = topicService.getTopicsBySubject(subjectId);
        return ResponseEntity.ok(ApiResponse.<List<TopicDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(topics)
                .build());
    }

    @Operation(summary = "Lấy chi tiết chương mục theo ID", description = "Xem thông tin tên, mô tả và thứ tự chương mục.")
    @GetMapping("/{topicId}")
    public ResponseEntity<ApiResponse<TopicDTO>> getTopicById(
            @PathVariable UUID subjectId,
            @PathVariable UUID topicId) {
        TopicDTO topic = topicService.getTopicById(topicId);
        return ResponseEntity.ok(ApiResponse.<TopicDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(topic)
                .build());
    }

    @Operation(summary = "Tạo chương mục kiến thức mới (Chỉ Admin/Giảng viên)", description = "Thêm chương mục mới vào môn học.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<TopicDTO>> createTopic(
            @PathVariable UUID subjectId,
            @Valid @RequestBody CreateTopicDTO createTopicDTO) {
        createTopicDTO.setSubjectId(subjectId);
        TopicDTO createdTopic = topicService.createTopic(createTopicDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<TopicDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Success")
                .data(createdTopic)
                .build());
    }

    @Operation(summary = "Cập nhật thông tin chương mục", description = "Chỉnh sửa tên, mô tả và thứ tự hiển thị của chương mục.")
    @PutMapping("/{topicId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<TopicDTO>> updateTopic(
            @PathVariable UUID subjectId,
            @PathVariable UUID topicId,
            @Valid @RequestBody CreateTopicDTO updateTopicDTO) {
        updateTopicDTO.setSubjectId(subjectId);
        TopicDTO updatedTopic = topicService.updateTopic(topicId, updateTopicDTO);
        return ResponseEntity.ok(ApiResponse.<TopicDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(updatedTopic)
                .build());
    }

    @Operation(summary = "Xóa chương mục kiến thức (Chỉ Admin)", description = "Xóa một chương mục kiến thức khỏi hệ thống.")
    @DeleteMapping("/{topicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTopic(
            @PathVariable UUID subjectId,
            @PathVariable UUID topicId) {
        topicService.deleteTopic(topicId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(null)
                .build());
    }
}
