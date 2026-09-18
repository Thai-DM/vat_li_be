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
@Table(name = "exam_matrix")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamMatrix {

    @Column(name = "matrix_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID matrixId;

    @Column(name = "subject_id")
    private java.util.UUID subjectId;

    @Column(name = "exam_type")
    @Enumerated(EnumType.STRING)
    private ExamType examType;

    @Column(name = "description")
    private String description;


}
