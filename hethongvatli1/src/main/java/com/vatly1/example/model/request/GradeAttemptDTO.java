package com.vatly1.example.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeAttemptDTO {
    @NotNull(message = "totalScore không được để trống")
    @DecimalMin(value = "0.0", message = "Điểm số không được nhỏ hơn 0")
    private BigDecimal totalScore;

    private String feedback;
}
