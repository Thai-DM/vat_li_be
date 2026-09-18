package com.vatly1.example.repository;

import com.vatly1.example.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IExamQuestionRepository extends JpaRepository<ExamQuestion, ExamQuestion.ExamQuestionId> {
    List<ExamQuestion> findByExamIdOrderByOrderIndexAsc(UUID examId);
    boolean existsByExamIdAndQuestionId(UUID examId, UUID questionId);
    void deleteByExamId(UUID examId);
    long countByExamId(UUID examId);
}
