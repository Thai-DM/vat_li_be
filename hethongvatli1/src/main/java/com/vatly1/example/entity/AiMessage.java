package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.AiSender;
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
@Table(name = "ai_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMessage {

    @Column(name = "message_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID messageId;

    @Column(name = "conversation_id")
    private java.util.UUID conversationId;

    @Column(name = "sender")
    @Enumerated(EnumType.STRING)
    private AiSender sender;

    @Column(name = "content_text")
    private String contentText;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "created_at")
    private java.time.Instant createdAt;


}