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
    boolean existsByQuestionId(UUID questionId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM ExamQuestion eq WHERE eq.examId = :examId AND eq.questionId = :questionId")
    void deleteByExamIdAndQuestionId(UUID examId, UUID questionId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM ExamQuestion eq WHERE eq.examId = :examId")
    void deleteByExamId(UUID examId);

    long countByExamId(UUID examId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT eq.examId FROM ExamQuestion eq WHERE eq.questionId IN :questionIds")
    List<UUID> findDistinctExamIdsByQuestionIdIn(@org.springframework.data.repository.query.Param("questionIds") List<UUID> questionIds);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT eq.questionId FROM ExamQuestion eq, Exam e WHERE eq.examId = e.examId AND e.examType = com.vatly1.example.entity.enums.ExamType.FINAL")
    List<UUID> findDistinctQuestionIdsInFinalExams();
}