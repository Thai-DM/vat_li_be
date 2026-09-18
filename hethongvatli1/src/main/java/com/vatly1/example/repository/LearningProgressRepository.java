package com.vatly1.example.repository;

import com.vatly1.example.entity.LearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearningProgressRepository extends JpaRepository<LearningProgress, UUID> {
    List<LearningProgress> findByClassIdAndStudentId(UUID classId, UUID studentId);
    List<LearningProgress> findByClassId(UUID classId);
    Optional<LearningProgress> findByStudentIdAndClassIdAndTopicId(UUID studentId, UUID classId, UUID topicId);
}
