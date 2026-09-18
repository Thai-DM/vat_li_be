package com.vatly1.example.repository;

import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.enums.DifficultyLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionBankRepository extends JpaRepository<QuestionBank, UUID>, JpaSpecificationExecutor<QuestionBank> {
    Page<QuestionBank> findBySubjectIdAndTopicIdAndDifficultyLevel(UUID subjectId, UUID topicId, DifficultyLevel difficultyLevel, Pageable pageable);
    Page<QuestionBank> findBySubjectIdAndTopicId(UUID subjectId, UUID topicId, Pageable pageable);
    Page<QuestionBank> findBySubjectIdAndDifficultyLevel(UUID subjectId, DifficultyLevel difficultyLevel, Pageable pageable);
    Page<QuestionBank> findBySubjectId(UUID subjectId, Pageable pageable);
    List<QuestionBank> findByTopicIdAndDifficultyLevel(UUID topicId, DifficultyLevel difficultyLevel);
    List<QuestionBank> findByTopicId(UUID topicId);
}