package com.vatly1.example.service;

import com.vatly1.example.dto.request.AddExamQuestionDTO;
import com.vatly1.example.dto.request.CreateExamDTO;
import com.vatly1.example.dto.ExamAttemptDTO;
import com.vatly1.example.dto.ExamDTO;
import com.vatly1.example.dto.request.SubmitAnswerDTO;

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
}
