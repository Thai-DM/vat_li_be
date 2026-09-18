package com.vatly1.example.repository;

import com.vatly1.example.entity.LearningMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, UUID> {
    List<LearningMaterial> findByTopicIdOrderByCreatedAtDesc(UUID topicId);
}
