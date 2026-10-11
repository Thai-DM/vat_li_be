package com.vatly1.example.repository;

import com.vatly1.example.entity.PracticeSessionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IPracticeSessionQuestionRepository extends JpaRepository<PracticeSessionQuestion, UUID> {
    List<PracticeSessionQuestion> findBySessionIdOrderByOrderIndexAsc(UUID sessionId);
    boolean existsBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
    boolean existsByQuestionId(UUID questionId);
    void deleteBySessionId(UUID sessionId);
}
