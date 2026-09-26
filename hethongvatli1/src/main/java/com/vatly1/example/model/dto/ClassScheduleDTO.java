package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.LessonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassScheduleDTO {
    private UUID scheduleId;
    private UUID classId;
    private String classCode;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private UUID semesterId;
    private UUID instructorId;
    private String instructorName;
    private Integer dayOfWeek; // 2: Thứ Hai, ..., 8: Chủ Nhật
    private String dayOfWeekText; // "Thứ Hai", "Thứ Ba", "Chủ Nhật"
    private Integer startPeriod;
    private Integer endPeriod;
    private LocalTime startTime;
    private LocalTime endTime;
    private String room;
    private String building;
    private LessonType lessonType;
    private String notes;
    private Instant createdAt;
}
