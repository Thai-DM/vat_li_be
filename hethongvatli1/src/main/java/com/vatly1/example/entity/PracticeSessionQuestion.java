package com.vatly1.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "practice_session_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "is_reviewed")
    private Boolean isReviewed;
}
