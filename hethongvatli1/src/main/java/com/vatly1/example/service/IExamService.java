package com.vatly1.example.service;

import com.vatly1.example.model.dto.ExamAttemptDTO;
import com.vatly1.example.model.dto.ExamAttemptSummaryDTO;
import com.vatly1.example.model.dto.ExamDTO;
import com.vatly1.example.model.dto.ExamQuestionDetailDTO;
import com.vatly1.example.model.request.AddExamQuestionDTO;
import com.vatly1.example.model.request.CreateExamDTO;
import com.vatly1.example.model.request.GradeAttemptDTO;
import com.vatly1.example.model.request.SubmitAnswerDTO;
import com.vatly1.example.model.request.UpdateExamDTO;

import java.util.List;
import java.util.UUID;

public interface IExamService {
    ExamDTO createExam(CreateExamDTO dto, UUID creatorId);
    int autoGenerateQuestions(UUID examId, UUID instructorId);
    void addQuestionToExam(UUID examId, AddExamQuestionDTO dto, UUID instructorId);
    List<ExamDTO> getExamsByClass(UUID classId, UUID currentUserId, String role);
    ExamDTO getExamById(UUID examId);
    ExamAttemptDTO startAttempt(UUID examId, UUID studentId);
    void submitAnswer(UUID attemptId, SubmitAnswerDTO dto, UUID studentId);
    ExamAttemptDTO submitAttempt(UUID attemptId, UUID studentId);
    ExamAttemptDTO getMyAttempt(UUID examId, UUID studentId);
    List<ExamAttemptDTO> getMyAttempts(UUID examId, UUID studentId);
    ExamAttemptDTO getAttemptDetail(UUID attemptId, UUID currentUserId, String role);

    List<ExamQuestionDetailDTO> getExamQuestions(UUID examId, UUID currentUserId, String role);
    void removeQuestionFromExam(UUID examId, UUID questionId, UUID instructorId);
    ExamDTO updateExam(UUID examId, UpdateExamDTO dto, UUID instructorId);
    void deleteExam(UUID examId, UUID instructorId);
    List<ExamAttemptSummaryDTO> getExamAttempts(UUID examId, UUID currentUserId, String role);
    ExamAttemptDTO gradeAttempt(UUID attemptId, GradeAttemptDTO dto, UUID instructorId);
}