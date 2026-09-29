package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamMatrixDTO {
    private UUID matrixId;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private String matrixName;
    private ExamType examType;
    private String description;
    private BigDecimal totalPoints;
    private int totalQuestions;
    private Instant createdAt;
    private List<ExamMatrixDetailDTO> details;
    private Map<String, Object> statistics; // Phân bố theo chương & phân bố theo độ khó
}
