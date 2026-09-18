package com.vatly1.example.repository;

import com.vatly1.example.entity.ExperimentConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExperimentConfirmationRepository extends JpaRepository<ExperimentConfirmation, UUID> {
    boolean existsBySubmissionId(UUID submissionId);
    Optional<ExperimentConfirmation> findBySubmissionId(UUID submissionId);
}
