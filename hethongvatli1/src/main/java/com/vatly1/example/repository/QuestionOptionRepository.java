package com.vatly1.example.repository;

import com.vatly1.example.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {
    List<QuestionOption> findByQuestionIdOrderByOrderIndexAsc(UUID questionId);
    List<QuestionOption> findByQuestionId(UUID questionId);
    List<QuestionOption> findByQuestionIdAndIsCorrectTrue(UUID questionId);
    void deleteByQuestionId(UUID questionId);
}
