package com.vatly1.example.service;

import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.model.dto.LearningMaterialDTO;
import com.vatly1.example.model.dto.StudentAgendaDTO;
import com.vatly1.example.model.dto.StudentExperimentAssignmentDTO;
import com.vatly1.example.model.dto.UpcomingTaskDTO;

import java.util.List;
import java.util.UUID;

public interface IStudentPortalService {
    List<StudentExperimentAssignmentDTO> getMyExperimentAssignments(UUID studentId);
    StudentAgendaDTO getMyAgenda(UUID studentId);
    List<LearningMaterialDTO> getMyMaterials(UUID studentId, UUID classId, UUID topicId, MaterialType type);
    List<UpcomingTaskDTO> getMyUpcomingTasks(UUID studentId);
}
