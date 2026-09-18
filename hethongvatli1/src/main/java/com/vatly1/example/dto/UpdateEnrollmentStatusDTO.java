package com.vatly1.example.dto;

import com.vatly1.example.entity.enums.EnrollmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEnrollmentStatusDTO {
    @NotNull(message = "Trạng thái không được để trống")
    private EnrollmentStatus status;
}
