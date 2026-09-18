package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.SubmissionStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "experiment_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperimentSubmission {

    @Column(name = "submission_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID submissionId;

    @Column(name = "assignment_id")
    private java.util.UUID assignmentId;

    @Column(name = "student_id")
    private java.util.UUID studentId;

    @Column(name = "submitted_at")
    private java.time.Instant submittedAt;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "file_id")
    private java.util.UUID fileId;

    @Column(name = "raw_data_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private com.fasterxml.jackson.databind.JsonNode rawDataJson;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SubmissionStatus status;


}