package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.AiMode;
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
@Table(name = "ai_conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConversation {

    @Column(name = "conversation_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID conversationId;

    @Column(name = "student_id")
    private java.util.UUID studentId;

    @Column(name = "class_id")
    private java.util.UUID classId;

    @Column(name = "topic_id")
    private java.util.UUID topicId;

    @Column(name = "mode")
    @Enumerated(EnumType.STRING)
    private AiMode mode;

    @Column(name = "started_at")
    private java.time.Instant startedAt;

    @Column(name = "ended_at")
    private java.time.Instant endedAt;


}
