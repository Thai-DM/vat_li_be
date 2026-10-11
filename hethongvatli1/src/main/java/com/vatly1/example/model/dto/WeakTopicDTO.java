package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.WeaknessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeakTopicDTO {
    private UUID topicId;
    private String topicName;
    private UUID chapterId;
    private String chapterName;
    private int totalAnswered;
    private int correctCount;
    private int wrongCount;
    private double accuracyRate;
    private WeaknessLevel weaknessLevel;
    private boolean hasEnoughData;
    private String recommendation;
}
