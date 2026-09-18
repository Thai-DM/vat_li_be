package com.vatly1.example.repository;

import com.vatly1.example.entity.EvidenceRepository;
import com.vatly1.example.entity.enums.EvidenceSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvidenceRepositoryJpaRepo extends JpaRepository<EvidenceRepository, UUID> {
    List<EvidenceRepository> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
    List<EvidenceRepository> findByStudentIdAndSourceType(UUID studentId, EvidenceSourceType type);
    List<EvidenceRepository> findByStudentIdInOrderByCreatedAtDesc(List<UUID> studentIds);
    boolean existsByStudentIdAndSourceTypeAndSourceId(UUID studentId, EvidenceSourceType type, UUID sourceId);
}