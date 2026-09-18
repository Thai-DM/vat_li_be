package com.vatly1.example.controller;

import com.vatly1.example.dto.dto.AiConversationDTO;
import com.vatly1.example.dto.dto.AiFeedbackDTO;
import com.vatly1.example.dto.dto.AiMessageDTO;
import com.vatly1.example.dto.response.ApiResponse;
import com.vatly1.example.dto.request.SendAiMessageDTO;
import com.vatly1.example.dto.request.StartAiConversationDTO;
import com.vatly1.example.service.IAiTutorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-tutor")
@RequiredArgsConstructor
@Tag(name = "AI Socratic Tutor", description = "APIs Trợ giảng AI Socratic tiếng Việt: khởi tạo hội thoại, gửi câu hỏi gợi mở, phản hồi")
@SecurityRequirement(name = "bearerAuth")
public class AiTutorController {

    private final IAiTutorService aiTutorService;

    @Operation(summary = "Khởi tạo phiên hội thoại mới với trợ giảng AI Socratic", description = "Mở phiên thảo luận bài tập, giải đáp khái niệm vật lý theo phương pháp Socratic.")
    @PostMapping("/conversations")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<AiConversationDTO>> startConversation(
            @Valid @RequestBody StartAiConversationDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        AiConversationDTO conversation = aiTutorService.startConversation(dto, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<AiConversationDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Conversation started")
                .data(conversation)
                .build());
    }

    @Operation(summary = "Gửi câu hỏi / tin nhắn cho trợ giảng AI", description = "Nhận phản hồi gợi mở, gợi ý tư duy kèm trích dẫn tài liệu học tập chính thức.")
    @PostMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<AiMessageDTO>> sendMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendAiMessageDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        AiMessageDTO reply = aiTutorService.sendMessage(conversationId, dto, currentUserId);
        return ResponseEntity.ok(ApiResponse.<AiMessageDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Message processed")
                .data(reply)
                .build());
    }

    @Operation(summary = "Lấy lịch sử tin nhắn trong phiên hội thoại AI", description = "Xem lại toàn bộ trao đổi giữa sinh viên và trợ giảng AI.")
    @GetMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<AiMessageDTO>>> getConversationHistory(
            @PathVariable UUID conversationId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        List<AiMessageDTO> history = aiTutorService.getConversationHistory(conversationId, currentUserId);
        return ResponseEntity.ok(ApiResponse.<List<AiMessageDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(history)
                .build());
    }

    @Operation(summary = "Lấy danh sách các phiên hội thoại AI của sinh viên", description = "Xem danh sách các phiên thảo luận trợ giảng AI của sinh viên đang đăng nhập.")
    @GetMapping("/conversations/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<AiConversationDTO>>> getMyConversations(
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        List<AiConversationDTO> conversations = aiTutorService.getMyConversations(currentUserId);
        return ResponseEntity.ok(ApiResponse.<List<AiConversationDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(conversations)
                .build());
    }

    @Operation(summary = "Kết thúc phiên hội thoại AI", description = "Đóng phiên thảo luận sau khi sinh viên đã giải quyết xong thắc mắc.")
    @PutMapping("/conversations/{conversationId}/end")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<AiConversationDTO>> endConversation(
            @PathVariable UUID conversationId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        AiConversationDTO closed = aiTutorService.endConversation(conversationId, currentUserId);
        return ResponseEntity.ok(ApiResponse.<AiConversationDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Conversation ended")
                .data(closed)
                .build());
    }

    @Operation(summary = "Gửi phản hồi / đánh giá câu trả lời của AI", description = "Đánh giá chất lượng trợ giảng (hữu ích, chưa rõ ràng, từ chối đúng/sai).")
    @PostMapping("/messages/{messageId}/feedback")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Void>> submitFeedback(
            @PathVariable UUID messageId,
            @Valid @RequestBody AiFeedbackDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        aiTutorService.submitFeedback(messageId, dto, currentUserId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Feedback submitted successfully")
                .data(null)
                .build());
    }
}