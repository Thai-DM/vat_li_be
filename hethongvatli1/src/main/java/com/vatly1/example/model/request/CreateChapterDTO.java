package com.vatly1.example.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChapterDTO {

    private UUID subjectId;

    @NotBlank(message = "Chapter code is required")
    @Size(max = 50, message = "Chapter code must not exceed 50 characters")
    private String chapterCode;

    @NotBlank(message = "Chapter name is required")
    @Size(max = 255, message = "Chapter name must not exceed 255 characters")
    private String chapterName;

    private String description;

    private Integer orderIndex;

    private Boolean isActive;
}
