package com.vatly1.example.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAgendaDTO {
    private List<ClassScheduleDTO> schedules;
    private List<StudentAgendaExamDTO> exams;
    private List<StudentAgendaExperimentDTO> experiments;
}
