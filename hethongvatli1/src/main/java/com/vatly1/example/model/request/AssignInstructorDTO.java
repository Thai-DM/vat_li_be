package com.vatly1.example.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignInstructorDTO {
    @NotNull(message = "ID giảng viên (instructorId) không được để trống")
    private UUID instructorId;
}
