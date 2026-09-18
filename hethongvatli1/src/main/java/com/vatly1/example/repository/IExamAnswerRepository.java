package com.vatly1.example.repository;

import com.vatly1.example.entity.ExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IExamAnswerRepository extends JpaRepository<ExamAnswer, UUID> {
    List<ExamAnswer> findByAttemptId(UUID attemptId);
    List<ExamAnswer> findByQuestionId(UUID questionId);
    Optional<ExamAnswer> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);
    boolean existsByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);
}