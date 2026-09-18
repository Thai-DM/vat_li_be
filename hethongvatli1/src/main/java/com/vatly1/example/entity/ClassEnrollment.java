package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.EnrollmentStatus;
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
@Table(name = "class_enrollments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassEnrollment {

    @Column(name = "enrollment_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID enrollmentId;

    @Column(name = "class_id")
    private java.util.UUID classId;

    @Column(name = "student_id")
    private java.util.UUID studentId;

    @CreationTimestamp
    @Column(name = "enrolled_at", updatable = false)
    private java.time.Instant enrolledAt;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private EnrollmentStatus status;


}
