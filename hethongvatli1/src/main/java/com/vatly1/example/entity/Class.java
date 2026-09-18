package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ClassStatus;
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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "classes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Class {

    @Column(name = "class_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID classId;

    @Column(name = "subject_id")
    private java.util.UUID subjectId;

    @Column(name = "semester_id")
    private java.util.UUID semesterId;

    @Column(name = "class_code")
    private String classCode;

    @Column(name = "instructor_id")
    private java.util.UUID instructorId;

    @Column(name = "max_students")
    private Integer maxStudents;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ClassStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.Instant createdAt;


}
