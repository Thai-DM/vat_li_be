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

@Entity
@Table(name = "experiment_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperimentAssignment {

    @Column(name = "assignment_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID assignmentId;

    @Column(name = "experiment_id")
    private java.util.UUID experimentId;

    @Column(name = "class_id")
    private java.util.UUID classId;

    @Column(name = "assigned_by")
    private java.util.UUID assignedBy;

    @Column(name = "due_date")
    private java.time.Instant dueDate;

    @Column(name = "instructions_override")
    private String instructionsOverride;

    @Column(name = "created_at")
    private java.time.Instant createdAt;


}
