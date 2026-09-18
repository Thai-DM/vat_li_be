package com.vatly1.example.repository;

import com.vatly1.example.entity.ExperimentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExperimentAssignmentRepository extends JpaRepository<ExperimentAssignment, UUID> {
    List<ExperimentAssignment> findByClassId(UUID classId);
    boolean existsByExperimentIdAndClassId(UUID experimentId, UUID classId);
}