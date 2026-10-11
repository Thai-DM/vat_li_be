package com.vatly1.example.model.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChapterDTO {

    @Size(max = 50, message = "Chapter code must not exceed 50 characters")
    private String chapterCode;

    @Size(max = 255, message = "Chapter name must not exceed 255 characters")
    private String chapterName;

    private String description;

    private Integer orderIndex;

    private Boolean isActive;
}
