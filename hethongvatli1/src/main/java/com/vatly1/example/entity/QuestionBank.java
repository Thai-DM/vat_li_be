package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "question_bank")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionBank {

    @Column(name = "question_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID questionId;

    @Column(name = "subject_id")
    private java.util.UUID subjectId;

    @Column(name = "topic_id")
    private java.util.UUID topicId;

    @Column(name = "question_type")
    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @Column(name = "content")
    private String content;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "difficulty_level")
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficultyLevel;

    @Column(name = "cognitive_level")
    private String cognitiveLevel;

    @Column(name = "created_by")
    private java.util.UUID createdBy;

    @Column(name = "approval_status")
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus;

    @Column(name = "created_at")
    private java.time.Instant createdAt;


}
