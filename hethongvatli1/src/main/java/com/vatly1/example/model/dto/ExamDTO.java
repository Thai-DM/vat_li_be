package com.vatly1.example.model.dto;

import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

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
public class ExamDTO {
    private UUID examId;
    private UUID classId;
    private UUID matrixId;
    private String title;
    private ExamType examType;
    private Integer durationMinutes;
    private Instant startTime;
    private Instant endTime;
    private UUID createdBy;
    private Instant createdAt;
    private Integer totalQuestions;
}