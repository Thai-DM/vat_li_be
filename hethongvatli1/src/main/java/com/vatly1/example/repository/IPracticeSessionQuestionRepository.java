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

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(psq) > 0 FROM PracticeSessionQuestion psq, PracticeSession ps WHERE psq.sessionId = ps.sessionId AND ps.studentId = :studentId AND psq.questionId = :questionId")
    boolean existsByStudentIdAndQuestionId(@org.springframework.data.repository.query.Param("studentId") UUID studentId, @org.springframework.data.repository.query.Param("questionId") UUID questionId);
}

