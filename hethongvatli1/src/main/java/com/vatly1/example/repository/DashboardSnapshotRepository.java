package com.vatly1.example.repository;

import com.vatly1.example.entity.DashboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshot, UUID> {
    Optional<DashboardSnapshot> findTopByClassIdAndStudentIdIsNullOrderByGeneratedAtDesc(UUID classId);
    Optional<DashboardSnapshot> findTopByClassIdAndStudentIdOrderByGeneratedAtDesc(UUID classId, UUID studentId);
    Optional<DashboardSnapshot> findTopByStudentIdOrderByGeneratedAtDesc(UUID studentId);
}