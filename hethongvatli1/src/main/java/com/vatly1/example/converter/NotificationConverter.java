package com.vatly1.example.converter;

import com.vatly1.example.entity.Notification;
import com.vatly1.example.entity.enums.NotificationType;
import com.vatly1.example.model.dto.NotificationDTO;
import org.springframework.stereotype.Component;

@Component
public class NotificationConverter {

    public static String getTypeDescription(NotificationType type) {
        if (type == null) {
            return "Thông báo";
        }
        return switch (type) {
            case EXAM_NEW -> "Bài thi mới";
            case EXAM_GRADED -> "Đã có điểm thi";
            case EXPERIMENT_NEW -> "Thí nghiệm mới";
            case EXPERIMENT_GRADED -> "Đã chấm điểm thí nghiệm";
            case SCHEDULE_REMINDER -> "Nhắc nhở lịch học";
            case MATERIAL_NEW -> "Tài liệu học tập mới";
            case ANNOUNCEMENT -> "Thông báo lớp học";
            case SYSTEM -> "Thông báo hệ thống";
        };
    }

    public NotificationDTO toDTO(Notification notification) {
        if (notification == null) {
            return null;
        }
        return NotificationDTO.builder()
                .notificationId(notification.getNotificationId())
                .userId(notification.getUserId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .type(notification.getType())
                .typeDescription(getTypeDescription(notification.getType()))
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
