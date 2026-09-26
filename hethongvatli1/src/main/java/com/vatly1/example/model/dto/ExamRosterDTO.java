package com.vatly1.example.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamRosterDTO {
    private UUID examId;
    private String examTitle;
    private UUID classId;
    private String classCode;
    private int totalParticipants;
    private int officialStudentCount;
    private int transferredStudentCount;
    private List<EnrollmentDTO> officialStudents;
    private List<ExamParticipantDTO> transferredStudents;
}
