package com.vatly1.example.repository;

import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.QuestionCloId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IQuestionCloRepository extends JpaRepository<QuestionClo, QuestionCloId> {

    List<QuestionClo> findByQuestionId(UUID questionId);

    List<QuestionClo> findByCloId(UUID cloId);

    List<QuestionClo> findBySubjectId(UUID subjectId);

    List<QuestionClo> findBySubjectIdAndQuestionIdIn(UUID subjectId, java.util.Collection<UUID> questionIds);

    boolean existsByQuestionIdAndCloId(UUID questionId, UUID cloId);

    void deleteByQuestionIdAndCloId(UUID questionId, UUID cloId);

    void deleteByQuestionId(UUID questionId);

    void deleteByCloId(UUID cloId);

    long countByCloId(UUID cloId);

    long countByQuestionId(UUID questionId);
}
