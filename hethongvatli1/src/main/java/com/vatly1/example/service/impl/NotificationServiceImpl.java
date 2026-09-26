package com.vatly1.example.service.impl;

import com.vatly1.example.converter.ClassScheduleConverter;
import com.vatly1.example.converter.NotificationConverter;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.ClassSchedule;
import com.vatly1.example.entity.Notification;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.NotificationType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.NotificationDTO;
import com.vatly1.example.model.dto.NotificationSummaryDTO;
import com.vatly1.example.model.request.CreateNotificationDTO;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassScheduleRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.INotificationRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements INotificationService {

    private final INotificationRepository notificationRepository;
    private final IClassRepository classRepository;
    private final IClassEnrollmentRepository enrollmentRepository;
    private final IClassStaffRepository classStaffRepository;
    private final IClassScheduleRepository classScheduleRepository;
    private final ISubjectRepository subjectRepository;
    private final NotificationConverter notificationConverter;

    @Override
    public Page<NotificationDTO> getMyNotifications(UUID userId, Boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications;

        if (unreadOnly != null && unreadOnly) {
            notifications = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return notifications.map(notificationConverter::toDTO);
    }

    @Override
    public NotificationSummaryDTO getMyNotificationSummary(UUID userId) {
        long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId);
        List<Notification> top5 = notificationRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId);
        List<NotificationDTO> dtoList = top5.stream()
                .map(notificationConverter::toDTO)
                .collect(Collectors.toList());

        return NotificationSummaryDTO.builder()
                .unreadCount(unreadCount)
                .latestNotifications(dtoList)
                .build();
    }

    @Override
    @Transactional
    public NotificationDTO markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByNotificationIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new CustomException("Không tìm thấy thông báo", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return notificationConverter.toDTO(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId, Instant.now());
    }

    @Override
    @Transactional
    public void deleteNotification(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByNotificationIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new CustomException("Không tìm thấy thông báo", HttpStatus.NOT_FOUND));

        notificationRepository.delete(notification);
    }

    @Override
    @Transactional
    public NotificationDTO sendNotification(UUID userId, String title, String content, NotificationType type, UUID referenceId, String referenceType) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .type(type != null ? type : NotificationType.SYSTEM)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        return notificationConverter.toDTO(saved);
    }

    @Override
    @Transactional
    public int sendNotificationToClass(UUID classId, CreateNotificationDTO dto, UUID senderId, String senderRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        UserRole role = UserRole.valueOf(senderRole.toUpperCase());
        if (role != UserRole.ADMIN) {
            boolean isOwner = clazz.getInstructorId() != null && clazz.getInstructorId().equals(senderId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(classId, senderId);
            if (!isOwner && !isStaff) {
                throw new CustomException("Bạn không có quyền gửi thông báo cho lớp này", HttpStatus.FORBIDDEN);
            }
        }

        NotificationType type = dto.getType() != null ? dto.getType() : NotificationType.ANNOUNCEMENT;
        UUID refId = dto.getReferenceId() != null ? dto.getReferenceId() : classId;
        String refType = dto.getReferenceType() != null ? dto.getReferenceType() : "CLASS";

        return sendNotificationToClass(classId, dto.getTitle(), dto.getContent(), type, refId, refType);
    }

    @Override
    @Transactional
    public int sendNotificationToClass(UUID classId, String title, String content, NotificationType type, UUID referenceId, String referenceType) {
        List<ClassEnrollment> enrollments = enrollmentRepository.findByClassIdAndStatus(classId, EnrollmentStatus.ACTIVE);
        if (enrollments.isEmpty()) {
            return 0;
        }

        List<Notification> notifications = new ArrayList<>();
        for (ClassEnrollment enrollment : enrollments) {
            notifications.add(Notification.builder()
                    .userId(enrollment.getStudentId())
                    .title(title)
                    .content(content)
                    .type(type != null ? type : NotificationType.ANNOUNCEMENT)
                    .referenceId(referenceId)
                    .referenceType(referenceType)
                    .isRead(false)
                    .build());
        }

        notificationRepository.saveAll(notifications);
        log.info("Sent {} notifications to class {}", notifications.size(), classId);
        return notifications.size();
    }

    @Override
    @Transactional
    public int generateUpcomingScheduleReminders(UUID studentId) {
        List<ClassEnrollment> enrollments = enrollmentRepository.findByStudentIdAndStatus(studentId, EnrollmentStatus.ACTIVE);
        if (enrollments.isEmpty()) {
            return 0;
        }

        List<UUID> classIds = enrollments.stream()
                .map(ClassEnrollment::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (classIds.isEmpty()) {
            return 0;
        }

        List<Class> classes = classRepository.findAllById(classIds);
        Map<UUID, Class> classMap = classes.stream().collect(Collectors.toMap(Class::getClassId, c -> c));

        List<ClassSchedule> schedules = classScheduleRepository.findByClassIdInOrderByDayOfWeekAscStartTimeAsc(classIds);
        if (schedules.isEmpty()) {
            return 0;
        }

        // Today's day of week in convention: 2=Mon, 3=Tue, ..., 7=Sat, 8=Sun
        int currentDayOfWeek = LocalDate.now().getDayOfWeek().getValue() + 1;
        int nextDayOfWeek = (currentDayOfWeek == 8) ? 2 : currentDayOfWeek + 1;

        Instant oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        int generatedCount = 0;

        for (ClassSchedule schedule : schedules) {
            // Remind if class is today or tomorrow
            if (schedule.getDayOfWeek() == currentDayOfWeek || schedule.getDayOfWeek() == nextDayOfWeek) {
                boolean alreadySent = notificationRepository.existsByUserIdAndTypeAndReferenceIdAndCreatedAtAfter(
                        studentId, NotificationType.SCHEDULE_REMINDER, schedule.getScheduleId(), oneDayAgo);

                if (!alreadySent) {
                    Class clazz = classMap.get(schedule.getClassId());
                    String classCode = clazz != null ? clazz.getClassCode() : "Học phần";
                    String subjectName = "";
                    if (clazz != null && clazz.getSubjectId() != null) {
                        Subject subj = subjectRepository.findById(clazz.getSubjectId()).orElse(null);
                        if (subj != null) {
                            subjectName = subj.getSubjectName() + " - ";
                        }
                    }

                    String dayText = (schedule.getDayOfWeek() == currentDayOfWeek) ? "Hôm nay" : "Ngày mai (" + ClassScheduleConverter.getDayOfWeekText(schedule.getDayOfWeek()) + ")";
                    String timeText = schedule.getStartTime() != null ? schedule.getStartTime().toString() : "";
                    String periodText = (schedule.getStartPeriod() != null && schedule.getEndPeriod() != null) ? " (Tiết " + schedule.getStartPeriod() + "-" + schedule.getEndPeriod() + ")" : "";
                    String roomText = schedule.getRoom() != null ? " tại " + schedule.getRoom() : "";

                    String title = "Nhắc lịch học: " + subjectName + classCode;
                    String content = String.format("Bạn có lịch học lớp %s vào %s lúc %s%s%s. Hãy chuẩn bị bài và tham gia đúng giờ!",
                            classCode, dayText, timeText, periodText, roomText);

                    Notification reminder = Notification.builder()
                            .userId(studentId)
                            .title(title)
                            .content(content)
                            .type(NotificationType.SCHEDULE_REMINDER)
                            .referenceId(schedule.getScheduleId())
                            .referenceType("SCHEDULE")
                            .isRead(false)
                            .build();

                    notificationRepository.save(reminder);
                    generatedCount++;
                }
            }
        }

        return generatedCount;
    }
}
