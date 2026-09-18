package com.vatly1.example.repository;

import com.vatly1.example.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {
    List<Topic> findBySubjectIdOrderByOrderIndexAsc(UUID subjectId);
    boolean existsBySubjectIdAndTopicName(UUID subjectId, String topicName);
}
