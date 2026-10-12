package com.vatly1.example.controller;

import com.vatly1.example.model.dto.ChapterDTO;
import com.vatly1.example.model.dto.TopicDTO;
import com.vatly1.example.model.request.ChapterStatusUpdateDTO;
import com.vatly1.example.model.request.CreateChapterDTO;
import com.vatly1.example.model.request.ReorderTopicsDTO;
import com.vatly1.example.model.request.UpdateChapterDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.IChapterService;
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
@RequiredArgsConstructor
@Tag(name = "Chapter Management", description = "APIs Quản lý chương học theo môn học (Subject-Centric)")
@SecurityRequirement(name = "bearerAuth")
public class ChapterController {

    private final IChapterService chapterService;

    @Operation(summary = "Lấy danh sách chương theo môn học", description = "Trả về danh sách các chương thuộc môn học, sắp xếp theo orderIndex. Hỗ trợ lọc activeOnly.")
    @GetMapping("/api/v1/subjects/{subjectId}/chapters")
    public ResponseEntity<ApiResponse<List<ChapterDTO>>> getChaptersBySubject(
            @PathVariable UUID subjectId,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        List<ChapterDTO> chapters = chapterService.getChaptersBySubject(subjectId, activeOnly);
        return ResponseEntity.ok(ApiResponse.<List<ChapterDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(chapters)
                .build());
    }

    @Operation(summary = "Tạo chương học mới (Chỉ Admin)", description = "Tạo chương học mới trong môn học.")
    @PostMapping("/api/v1/subjects/{subjectId}/chapters")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChapterDTO>> createChapter(
            @PathVariable UUID subjectId,
            @Valid @RequestBody CreateChapterDTO createChapterDTO) {
        createChapterDTO.setSubjectId(subjectId);
        ChapterDTO created = chapterService.createChapter(subjectId, createChapterDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ChapterDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo chương học thành công")
                .data(created)
                .build());
    }

    @Operation(summary = "Lấy chi tiết chương học theo ID", description = "Xem thông tin chi tiết của một chương học.")
    @GetMapping("/api/v1/chapters/{chapterId}")
    public ResponseEntity<ApiResponse<ChapterDTO>> getChapterById(@PathVariable UUID chapterId) {
        ChapterDTO chapter = chapterService.getChapterById(chapterId);
        return ResponseEntity.ok(ApiResponse.<ChapterDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(chapter)
                .build());
    }

    @Operation(summary = "Cập nhật chương học (Chỉ Admin)", description = "Chỉnh sửa mã chương, tên chương, mô tả, thứ tự hiển thị.")
    @PutMapping("/api/v1/chapters/{chapterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChapterDTO>> updateChapter(
            @PathVariable UUID chapterId,
            @Valid @RequestBody UpdateChapterDTO updateChapterDTO) {
        ChapterDTO updated = chapterService.updateChapter(chapterId, updateChapterDTO);
        return ResponseEntity.ok(ApiResponse.<ChapterDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật chương học thành công")
                .data(updated)
                .build());
    }

    @Operation(summary = "Bật/tắt trạng thái hoạt động của chương học (Chỉ Admin)", description = "Kích hoạt hoặc ngưng hoạt động chương học.")
    @PutMapping("/api/v1/chapters/{chapterId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChapterDTO>> updateChapterStatus(
            @PathVariable UUID chapterId,
            @RequestParam(required = false) Boolean isActive,
            @RequestBody(required = false) ChapterStatusUpdateDTO statusDto) {
        Boolean activeVal = isActive;
        if (activeVal == null && statusDto != null) {
            activeVal = statusDto.getIsActive();
        }
        ChapterDTO updated = chapterService.updateChapterStatus(chapterId, activeVal);
        return ResponseEntity.ok(ApiResponse.<ChapterDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật trạng thái chương học thành công")
                .data(updated)
                .build());
    }

    @Operation(summary = "Xóa chương học rỗng (Chỉ Admin)", description = "Xóa chương học khi không còn chứa bài học nào.")
    @DeleteMapping("/api/v1/chapters/{chapterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteChapter(@PathVariable UUID chapterId) {
        chapterService.deleteChapter(chapterId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa chương học thành công")
                .build());
    }

    @Operation(summary = "Lấy danh sách bài học thuộc chương", description = "Trả về danh sách các bài học (topics) thuộc chương học, sắp xếp theo thứ tự orderIndex.")
    @GetMapping("/api/v1/chapters/{chapterId}/topics")
    public ResponseEntity<ApiResponse<List<TopicDTO>>> getTopicsByChapter(@PathVariable UUID chapterId) {
        List<TopicDTO> topics = chapterService.getTopicsByChapter(chapterId);
        return ResponseEntity.ok(ApiResponse.<List<TopicDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(topics)
                .build());
    }

    @Operation(summary = "Sắp xếp lại thứ tự bài học trong chương (Chỉ Admin)", description = "Cập nhật lại orderIndex cho toàn bộ bài học trong chương theo danh sách ID.")
    @PutMapping("/api/v1/chapters/{chapterId}/topics/reorder")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<TopicDTO>>> reorderTopics(
            @PathVariable UUID chapterId,
            @Valid @RequestBody ReorderTopicsDTO dto) {
        List<TopicDTO> reordered = chapterService.reorderTopics(chapterId, dto);
        return ResponseEntity.ok(ApiResponse.<List<TopicDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Sắp xếp lại thứ tự bài học thành công")
                .data(reordered)
                .build());
    }
}
