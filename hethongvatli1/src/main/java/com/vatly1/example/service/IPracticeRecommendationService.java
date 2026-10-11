package com.vatly1.example.service;

import com.vatly1.example.model.dto.ClassWeakTopicsSummaryDTO;
import com.vatly1.example.model.dto.PracticeSessionDTO;
import com.vatly1.example.model.dto.PracticeSessionResultDTO;
import com.vatly1.example.model.dto.WeakTopicDTO;
import com.vatly1.example.model.request.CreatePracticeRecommendationRequest;
import com.vatly1.example.model.request.SubmitSessionAnswerRequest;

import java.util.List;
import java.util.UUID;

public interface IPracticeRecommendationService {

    /**
     * Phân tích điểm yếu theo từng Topic cho học sinh trong phạm vi môn học của lớp.
     */
    List<WeakTopicDTO> getStudentWeakTopics(UUID classId, UUID studentId);

    /**
     * Tạo phiên luyện tập thích ứng từ Ngân hàng câu hỏi dựa trên các Topic yếu.
     */
    PracticeSessionDTO createAdaptivePracticeSession(UUID classId, CreatePracticeRecommendationRequest request, UUID studentId);

    /**
     * Lấy thông tin phiên luyện tập hiện hành (ẩn đáp án đúng khi đang làm bài).
     */
    PracticeSessionDTO getPracticeSession(UUID sessionId, UUID studentId);

    /**
     * Lưu tạm câu trả lời của học sinh cho một câu hỏi trong phiên.
     */
    void saveAnswer(UUID sessionId, SubmitSessionAnswerRequest request, UUID studentId);

    /**
     * Nộp bài và chấm điểm tự động (hỗ trợ MCQ và FILL_BLANK, tôn trọng ClassSettings).
     */
    PracticeSessionResultDTO submitSession(UUID sessionId, UUID studentId);

    /**
     * Xem kết quả bài làm và thống kê tiến bộ của phiên luyện tập.
     */
    PracticeSessionResultDTO getSessionResult(UUID sessionId, UUID studentId);

    /**
     * Thống kê tổng hợp điểm yếu của các học sinh trong lớp dành cho Giảng viên phụ trách.
     */
    ClassWeakTopicsSummaryDTO getClassWeakTopicsSummary(UUID classId, UUID currentUserId, String currentUserRole);
}
