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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "class_enrollments", uniqueConstraints = {
    @UniqueConstraint(name = "uq_enrollment_class_student", columnNames = {"class_id", "student_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassEnrollment {

    @Column(name = "enrollment_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID enrollmentId;

    @Column(name = "class_id", nullable = false)
    private java.util.UUID classId;

    @Column(name = "student_id", nullable = false)
    private java.util.UUID studentId;

    @Column(name = "subject_id", nullable = false)
    private java.util.UUID subjectId;

    @Column(name = "semester_id", nullable = false)
    private java.util.UUID semesterId;

    @CreationTimestamp
    @Column(name = "enrolled_at", updatable = false)
    private java.time.Instant enrolledAt;

    @Column(name = "status")
    @jakarta.persistence.Convert(converter = com.vatly1.example.entity.enums.EnrollmentStatusConverter.class)
    private EnrollmentStatus status;
}