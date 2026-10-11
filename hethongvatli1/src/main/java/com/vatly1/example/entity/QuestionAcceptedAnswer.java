package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_accepted_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionAcceptedAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "accepted_answer_id")
    private UUID acceptedAnswerId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "accepted_text", nullable = false, length = 500)
    private String acceptedText;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
