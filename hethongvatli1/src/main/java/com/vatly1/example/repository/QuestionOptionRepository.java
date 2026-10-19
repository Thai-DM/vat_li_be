package com.vatly1.example.repository;

import com.vatly1.example.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {
    List<QuestionOption> findByQuestionIdOrderByOrderIndexAsc(UUID questionId);
    List<QuestionOption> findByQuestionIdIn(java.util.Collection<UUID> questionIds);
    List<QuestionOption> findByQuestionId(UUID questionId);
    List<QuestionOption> findByQuestionIdAndIsCorrectTrue(UUID questionId);
    boolean existsByOptionIdAndQuestionId(UUID optionId, UUID questionId);
    void deleteByQuestionId(UUID questionId);

    @org.springframework.data.jpa.repository.Query("SELECT o FROM QuestionOption o WHERE (o.mediaUrl IS NOT NULL AND o.mediaUrl LIKE %:key%) OR (o.optionText IS NOT NULL AND o.optionText LIKE %:key%)")
    List<QuestionOption> findOptionsReferencingFile(@org.springframework.data.repository.query.Param("key") String key);
}