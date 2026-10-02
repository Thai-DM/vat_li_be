package com.vatly1.example.repository;

import com.vatly1.example.entity.ExperimentScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExperimentScoreRepository extends JpaRepository<ExperimentScore, UUID> {
    List<ExperimentScore> findBySubmissionId(UUID submissionId);
    Optional<ExperimentScore> findBySubmissionIdAndRubricId(UUID submissionId, UUID rubricId);
    boolean existsBySubmissionIdAndRubricId(UUID submissionId, UUID rubricId);
    void deleteBySubmissionId(UUID submissionId);
}
