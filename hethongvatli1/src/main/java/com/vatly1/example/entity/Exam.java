package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ExamType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exams")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {

    @Column(name = "exam_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID examId;

    @Column(name = "class_id")
    private java.util.UUID classId;

    @Column(name = "matrix_id")
    private java.util.UUID matrixId;

    @Column(name = "title")
    private String title;

    @Column(name = "exam_type")
    @Enumerated(EnumType.STRING)
    private ExamType examType;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "start_time")
    private java.time.Instant startTime;

    @Column(name = "end_time")
    private java.time.Instant endTime;

    @Column(name = "created_by")
    private java.util.UUID createdBy;

    @Column(name = "created_at")
    private java.time.Instant createdAt;


}
