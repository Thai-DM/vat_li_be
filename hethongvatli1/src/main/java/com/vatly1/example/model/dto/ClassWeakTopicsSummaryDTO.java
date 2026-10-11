package com.vatly1.example.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassWeakTopicsSummaryDTO {
    private UUID classId;
    private String classCode;
    private UUID subjectId;
    private int totalEnrolledStudents;
    private List<TopicWeaknessSummaryDTO> topics;
}
