package com.vatly1.example.repository;

import com.vatly1.example.entity.ExperimentRubric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExperimentRubricRepository extends JpaRepository<ExperimentRubric, UUID> {
    List<ExperimentRubric> findByExperimentId(UUID experimentId);
    Optional<ExperimentRubric> findByExperimentIdAndCriteriaName(UUID experimentId, String criteriaName);
}
