package com.vatly1.example.service;

import com.vatly1.example.model.dto.ExamDTO;
import com.vatly1.example.model.dto.ExamParticipantDTO;
import com.vatly1.example.model.dto.ExamRosterDTO;
import com.vatly1.example.model.request.TransferStudentDTO;

import java.util.List;
import java.util.UUID;

public interface IExamParticipantService {

    ExamParticipantDTO transferStudentToExam(UUID examId, TransferStudentDTO dto, UUID approverId, String approverRole);

    void removeTransferredStudent(UUID examId, UUID studentId, UUID approverId, String approverRole);

    ExamRosterDTO getExamRoster(UUID examId, UUID currentUserId, String currentUserRole);

    List<ExamDTO> getTransferredExamsForStudent(UUID studentId);

    boolean isStudentEligibleForExam(UUID examId, UUID studentId);
}
