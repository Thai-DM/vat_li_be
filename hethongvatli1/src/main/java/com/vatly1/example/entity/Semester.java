package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "semesters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Semester {

    @Column(name = "semester_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID semesterId;

    @Column(name = "semester_code")
    private String semesterCode;

    @Column(name = "semester_name")
    private String semesterName;

    @Column(name = "academic_year")
    private String academicYear;

    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    @Column(name = "end_date")
    private java.time.LocalDate endDate;

    @Column(name = "is_current")
    private Boolean isCurrent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.Instant createdAt;


}
