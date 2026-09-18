package com.vatly1.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateClassDTO {
    @NotNull(message = "Môn học không được để trống")
    private java.util.UUID subjectId;

    @NotNull(message = "Học kỳ không được để trống")
    private java.util.UUID semesterId;

    @NotBlank(message = "Mã lớp không được để trống")
    private String classCode;

    private Integer maxStudents;
}
