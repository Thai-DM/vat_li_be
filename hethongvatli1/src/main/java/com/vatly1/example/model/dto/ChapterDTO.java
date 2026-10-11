package com.vatly1.example.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDTO {
    private UUID chapterId;
    private UUID subjectId;
    private String chapterCode;
    private String chapterName;
    private String description;
    private Integer orderIndex;
    private Boolean isActive;
    private Long topicCount;
    private Instant createdAt;
    private Instant updatedAt;
}
