package com.vatly1.example.service;

import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.UpdateExamMatrixDTO;
import com.vatly1.example.model.response.MatrixValidationResultDTO;

import java.util.List;
import java.util.UUID;

public interface IExamMatrixService {

    List<ExamMatrixDTO> getExamMatrices(UUID classId, UUID subjectId);

    ExamMatrixDTO createExamMatrix(CreateExamMatrixDTO dto, UUID creatorId);

    ExamMatrixDTO getExamMatrixById(UUID matrixId);

    ExamMatrixDTO updateExamMatrix(UUID matrixId, UpdateExamMatrixDTO dto, UUID updaterId);

    void deleteExamMatrix(UUID matrixId, UUID deleterId);

    MatrixValidationResultDTO validateExamMatrix(UUID matrixId);
}
