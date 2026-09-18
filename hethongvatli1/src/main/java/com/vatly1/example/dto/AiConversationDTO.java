package com.vatly1.example.dto;

import com.vatly1.example.entity.enums.AiMode;
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
public class AiConversationDTO {
    private UUID conversationId;
    private UUID studentId;
    private UUID classId;
    private UUID topicId;
    private AiMode mode;
    private Instant startedAt;
    private Instant endedAt;
    private Integer messageCount;
}
