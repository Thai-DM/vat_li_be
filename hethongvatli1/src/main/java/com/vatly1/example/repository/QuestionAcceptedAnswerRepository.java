package com.vatly1.example.repository;

import com.vatly1.example.entity.QuestionAcceptedAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionAcceptedAnswerRepository extends JpaRepository<QuestionAcceptedAnswer, UUID> {
    List<QuestionAcceptedAnswer> findByQuestionIdOrderByOrderIndexAsc(UUID questionId);
    List<QuestionAcceptedAnswer> findByQuestionId(UUID questionId);
    void deleteByQuestionId(UUID questionId);
    boolean existsByQuestionId(UUID questionId);
}
