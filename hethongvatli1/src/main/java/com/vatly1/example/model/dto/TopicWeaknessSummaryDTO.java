package com.vatly1.example.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicWeaknessSummaryDTO {
    private UUID topicId;
    private String topicName;
    private UUID chapterId;
    private String chapterName;
    private int totalStudentsWithData;
    private int studentsNeedingPractice;
    private double needPracticePercentage;
    private double averageAccuracyRate;
}
