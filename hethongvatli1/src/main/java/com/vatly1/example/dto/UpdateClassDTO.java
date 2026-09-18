package com.vatly1.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateClassDTO {
    @NotBlank(message = "Mã lớp không được để trống")
    private String classCode;

    private Integer maxStudents;
}
