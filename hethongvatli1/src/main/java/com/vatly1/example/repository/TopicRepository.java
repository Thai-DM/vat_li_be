package com.vatly1.example.repository;

import com.vatly1.example.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {
    List<Topic> findBySubjectIdOrderByOrderIndexAsc(UUID subjectId);
    List<Topic> findAllByOrderByOrderIndexAsc();
    boolean existsBySubjectIdAndTopicName(UUID subjectId, String topicName);

    List<Topic> findByChapterIdOrderByOrderIndexAsc(UUID chapterId);
    List<Topic> findBySubjectIdAndChapterIdIsNullOrderByOrderIndexAsc(UUID subjectId);
    long countByChapterId(UUID chapterId);
    boolean existsByChapterId(UUID chapterId);

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT t.topic_id as topicId, t.subject_id as subjectId, t.chapter_id as chapterId,
               t.topic_name as topicName, t.order_index as orderIndex, t.description as description,
               c.chapter_name as chapterName
        FROM topics t
        LEFT JOIN chapters c ON t.chapter_id = c.chapter_id
        WHERE t.subject_id = :subjectId
        ORDER BY t.order_index ASC
        """, nativeQuery = true)
    List<com.vatly1.example.repository.projection.TopicWithChapterProjection> findTopicsWithChapterBySubjectId(@org.springframework.data.repository.query.Param("subjectId") UUID subjectId);
}