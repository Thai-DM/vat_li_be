package com.vatly1.example.repository;

import com.vatly1.example.entity.LearningMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, UUID> {
    List<LearningMaterial> findByTopicIdOrderByCreatedAtDesc(UUID topicId);
    List<LearningMaterial> findByTopicIdIn(List<UUID> topicIds);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE learning_materials SET type = 'PDF' WHERE UPPER(type) IN ('DOCUMENT', 'DOC', 'DOCX')", nativeQuery = true)
    int migrateLegacyTypes();
}