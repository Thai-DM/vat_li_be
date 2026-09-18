package com.vatly1.example.repository;

import com.vatly1.example.entity.QuestionStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionStatRepository extends JpaRepository<QuestionStat, UUID> {

    @Query("SELECT qs FROM QuestionStat qs, QuestionBank q " +
            "WHERE qs.questionId = q.questionId " +
            "AND (:subjectId IS NULL OR q.subjectId = :subjectId) " +
            "AND (:topicId IS NULL OR q.topicId = :topicId) " +
            "AND (:minUsed IS NULL OR qs.timesUsed >= :minUsed) " +
            "ORDER BY qs.correctRate ASC")
    List<QuestionStat> filterStats(@Param("subjectId") UUID subjectId,
                                  @Param("topicId") UUID topicId,
                                  @Param("minUsed") Integer minUsed);
}
