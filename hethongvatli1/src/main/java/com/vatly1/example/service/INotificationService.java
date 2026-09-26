package com.vatly1.example.service;

import com.vatly1.example.entity.enums.NotificationType;
import com.vatly1.example.model.dto.NotificationDTO;
import com.vatly1.example.model.dto.NotificationSummaryDTO;
import com.vatly1.example.model.request.CreateNotificationDTO;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface INotificationService {

    Page<NotificationDTO> getMyNotifications(UUID userId, Boolean unreadOnly, int page, int size);

    NotificationSummaryDTO getMyNotificationSummary(UUID userId);

    NotificationDTO markAsRead(UUID notificationId, UUID userId);

    void markAllAsRead(UUID userId);

    void deleteNotification(UUID notificationId, UUID userId);

    NotificationDTO sendNotification(UUID userId, String title, String content, NotificationType type, UUID referenceId, String referenceType);

    int sendNotificationToClass(UUID classId, CreateNotificationDTO dto, UUID senderId, String senderRole);

    int sendNotificationToClass(UUID classId, String title, String content, NotificationType type, UUID referenceId, String referenceType);

    int generateUpcomingScheduleReminders(UUID studentId);
}
