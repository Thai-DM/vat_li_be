package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAgendaExamDTO {
    private UUID examId;
    private String title;
    private UUID classId;
    private String classCode;
    private ExamType examType;
    private Integer durationMinutes;
    private Instant startTime;
    private Instant endTime;
    @com.fasterxml.jackson.annotation.JsonProperty("isTransferred")
    private boolean isTransferred;
    private String status;
}
