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

@Entity
@Table(name = "ai_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFeedback {

    @Column(name = "feedback_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID feedbackId;

    @Column(name = "message_id")
    private java.util.UUID messageId;

    @Column(name = "student_id")
    private java.util.UUID studentId;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_at")
    private java.time.Instant createdAt;


}