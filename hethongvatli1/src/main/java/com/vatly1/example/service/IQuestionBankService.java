package com.vatly1.example.service;

import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.model.dto.QuestionBankDTO;
import com.vatly1.example.model.response.QuestionImportResultDTO;
import com.vatly1.example.entity.enums.DifficultyLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IQuestionBankService {
    Page<QuestionBankDTO> getQuestions(UUID subjectId, UUID topicId, DifficultyLevel difficultyLevel, Pageable pageable);
    Page<QuestionBankDTO> getQuestions(UUID subjectId, UUID chapterId, UUID topicId, DifficultyLevel difficultyLevel, Pageable pageable);
    java.util.List<QuestionBankDTO> getApprovedQuestionsBySubjectAndTopic(UUID subjectId, UUID topicId);
    QuestionBankDTO getQuestionById(UUID questionId);
    QuestionBankDTO createQuestion(CreateQuestionDTO dto, UUID currentUserId);
    QuestionBankDTO createQuestion(CreateQuestionDTO dto, UUID currentUserId, String role);
    QuestionBankDTO updateQuestion(UUID questionId, CreateQuestionDTO dto, UUID currentUserId, String role);
    QuestionBankDTO submitQuestionForReview(UUID questionId, UUID currentUserId, String role, String comment);
    QuestionBankDTO approveQuestion(UUID questionId);
    QuestionBankDTO approveQuestion(UUID questionId, UUID reviewerId, String role, String comment);
    QuestionBankDTO rejectQuestion(UUID questionId, String rejectionReason, UUID reviewerId, String role, String comment);
    void deleteQuestion(UUID questionId, UUID currentUserId, String role);
    QuestionImportResultDTO importQuestionsFromExcel(MultipartFile file, UUID subjectId, UUID topicId, UUID currentUserId);
    byte[] downloadQuestionExcelTemplate();
    java.util.List<com.vatly1.example.model.dto.QuestionApprovalDTO> getQuestionApprovalHistory(UUID questionId, UUID currentUserId, String role);
    QuestionBankDTO assignClosToQuestion(UUID questionId, java.util.List<UUID> cloIds, UUID currentUserId, String role);
}