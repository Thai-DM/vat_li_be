package com.vatly1.example.service;

import com.vatly1.example.model.request.CreateExperimentAssignmentDTO;
import com.vatly1.example.model.request.CreateExperimentDTO;
import com.vatly1.example.model.dto.ExperimentAssignmentDTO;
import com.vatly1.example.model.dto.ExperimentDTO;
import com.vatly1.example.model.request.SubmitExperimentDTO;

import com.vatly1.example.model.request.GradeSubmissionDTO;

import java.util.List;
import java.util.UUID;

public interface IExperimentService {
    List<ExperimentDTO> getExperimentsBySubject(UUID subjectId);
    ExperimentDTO getExperimentById(UUID experimentId);
    ExperimentDTO createExperiment(CreateExperimentDTO dto);
    
    ExperimentAssignmentDTO assignExperiment(UUID experimentId, CreateExperimentAssignmentDTO dto, UUID assignedBy);
    void submitExperiment(UUID assignmentId, SubmitExperimentDTO dto, UUID studentId);
    void gradeSubmission(UUID submissionId, GradeSubmissionDTO scoreDTO, UUID graderId);
    void confirmSubmission(UUID submissionId, String note, UUID instructorId);
}