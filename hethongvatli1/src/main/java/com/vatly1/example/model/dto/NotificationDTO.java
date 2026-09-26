package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    private UUID notificationId;
    private UUID userId;
    private String title;
    private String content;
    private NotificationType type;
    private String typeDescription;
    private UUID referenceId;
    private String referenceType;
    private Boolean isRead;
    private Instant readAt;
    private Instant createdAt;
}
