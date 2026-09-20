package com.vatly1.example.model.request;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSubjectDTO {
    @NotBlank(message = "Tên môn học không được để trống")
    private String subjectName;

    private String description;
}