package com.vatly1.example.dto;

import com.vatly1.example.entity.enums.AiSender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageDTO {
    private UUID messageId;
    private UUID conversationId;
    private AiSender sender;
    private String contentText;
    private String audioUrl;
    private Instant createdAt;
}
