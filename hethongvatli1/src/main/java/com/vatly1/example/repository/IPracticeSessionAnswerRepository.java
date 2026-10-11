package com.vatly1.example.repository;

import com.vatly1.example.entity.PracticeSessionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IPracticeSessionAnswerRepository extends JpaRepository<PracticeSessionAnswer, UUID> {
    List<PracticeSessionAnswer> findBySessionId(UUID sessionId);
    Optional<PracticeSessionAnswer> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
    List<PracticeSessionAnswer> findBySessionIdIn(List<UUID> sessionIds);
    void deleteBySessionId(UUID sessionId);
}
