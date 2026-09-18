package com.vatly1.example.repository;

import com.vatly1.example.entity.ExperimentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExperimentSubmissionRepository extends JpaRepository<ExperimentSubmission, UUID> {
    List<ExperimentSubmission> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    List<ExperimentSubmission> findByAssignmentId(UUID assignmentId);
    List<ExperimentSubmission> findByStudentId(UUID studentId);
}