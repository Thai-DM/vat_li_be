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
@Table(name = "experiment_confirmations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperimentConfirmation {

    @Column(name = "confirmation_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID confirmationId;

    @Column(name = "submission_id", nullable = false, unique = true)
    private java.util.UUID submissionId;

    @Column(name = "instructor_id")
    private java.util.UUID instructorId;

    @Column(name = "confirmed_at")
    private java.time.Instant confirmedAt;

    @Column(name = "note")
    private String note;


}