package com.vatly1.example.service;

import com.vatly1.example.dto.request.CreateQuestionDTO;
import com.vatly1.example.dto.dto.QuestionBankDTO;
import com.vatly1.example.dto.response.QuestionImportResultDTO;
import com.vatly1.example.entity.enums.DifficultyLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IQuestionBankService {
    Page<QuestionBankDTO> getQuestions(UUID subjectId, UUID topicId, DifficultyLevel difficultyLevel, Pageable pageable);
    QuestionBankDTO getQuestionById(UUID questionId);
    QuestionBankDTO createQuestion(CreateQuestionDTO dto, UUID currentUserId);
    QuestionBankDTO updateQuestion(UUID questionId, CreateQuestionDTO dto, UUID currentUserId, String role);
    QuestionBankDTO approveQuestion(UUID questionId);
    void deleteQuestion(UUID questionId, UUID currentUserId, String role);
    QuestionImportResultDTO importQuestionsFromExcel(MultipartFile file, UUID subjectId, UUID topicId, UUID currentUserId);
    byte[] downloadQuestionExcelTemplate();
}