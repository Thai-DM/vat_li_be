package com.vatly1.example.model.request;

import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

import com.vatly1.example.entity.enums.ExamType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateExamDTO {

    @NotNull(message = "Class ID is required")
    private UUID classId;

    private UUID matrixId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Exam type is required")
    private ExamType examType;

    @NotNull(message = "Duration in minutes is required")
    private Integer durationMinutes;

    private Instant startTime;

    private Instant endTime;
}