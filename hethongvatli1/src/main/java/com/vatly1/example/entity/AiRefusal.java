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
@Table(name = "ai_refusals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRefusal {

    @Column(name = "refusal_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID refusalId;

    @Column(name = "message_id")
    private java.util.UUID messageId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "missing_topic_hint")
    private String missingTopicHint;

    @Column(name = "created_at")
    private java.time.Instant createdAt;


}