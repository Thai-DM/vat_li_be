package com.vatly1.example.repository;

import com.vatly1.example.entity.Notification;
import com.vatly1.example.entity.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface INotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(UUID userId, Boolean isRead, Pageable pageable);

    List<Notification> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndIsReadFalse(UUID userId);

    Optional<Notification> findByNotificationIdAndUserId(UUID notificationId, UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);

    void deleteByNotificationIdAndUserId(UUID notificationId, UUID userId);

    boolean existsByUserIdAndTypeAndReferenceIdAndCreatedAtAfter(UUID userId, NotificationType type, UUID referenceId, Instant after);
}
