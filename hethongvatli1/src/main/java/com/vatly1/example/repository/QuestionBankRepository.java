package com.vatly1.example.repository;

import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.enums.ApprovalStatus;
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
    long countByTopicIdAndDifficultyLevel(UUID topicId, DifficultyLevel difficultyLevel);
    long countByTopicId(UUID topicId);

    List<QuestionBank> findByTopicIdAndDifficultyLevelAndApprovalStatus(UUID topicId, DifficultyLevel difficultyLevel, ApprovalStatus approvalStatus);
    List<QuestionBank> findByTopicIdAndApprovalStatus(UUID topicId, ApprovalStatus approvalStatus);
    long countByTopicIdAndDifficultyLevelAndApprovalStatus(UUID topicId, DifficultyLevel difficultyLevel, ApprovalStatus approvalStatus);
    long countByTopicIdAndApprovalStatus(UUID topicId, ApprovalStatus approvalStatus);

    List<QuestionBank> findBySubjectIdAndTopicIdInAndApprovalStatus(UUID subjectId, java.util.Collection<UUID> topicIds, ApprovalStatus approvalStatus);
    List<QuestionBank> findBySubjectIdAndApprovalStatus(UUID subjectId, ApprovalStatus approvalStatus);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT q FROM QuestionBank q WHERE q.questionId = :id")
    java.util.Optional<QuestionBank> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query("SELECT q FROM QuestionBank q WHERE (q.mediaUrl IS NOT NULL AND q.mediaUrl LIKE %:key%) OR (q.content IS NOT NULL AND q.content LIKE %:key%)")
    List<QuestionBank> findQuestionsReferencingFile(@org.springframework.data.repository.query.Param("key") String key);
}