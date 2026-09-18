package com.vatly1.example.dto.request;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLearningProgressDTO {
    
    @NotNull(message = "Topic ID is required")
    private UUID topicId;
    
    @NotNull(message = "Class ID is required")
    private UUID classId;
    
    @NotNull(message = "Progress percent is required")
    @Min(value = 0, message = "Progress must be at least 0")
    @Max(value = 100, message = "Progress cannot exceed 100")
    private BigDecimal progressPercent;
}