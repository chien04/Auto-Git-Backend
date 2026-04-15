package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.Notification;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.NotificationRepository;
import com.example.auto_git_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final SimpMessagingTemplate template;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void notifyTeacherOnSubmission(Long teacherId, Long studentId) {
        String message = String.format("Sinh viên %s vừa nộp bài.", studentId);
        Notification notification = createNotification(
                teacherId,
                "SUBMIT",
                "Bài nộp mới",
                message
        );

        log.info("Sending submission to teacher {}", teacherId);
        template.convertAndSendToUser(
                teacherId.toString(),
                "/queue/notifications",
                toPayload(notification)
        );
    }

    @Transactional
    public void notifyStudentOnGraded(Long studentId, Double score) {
        String message = String.format("Bài của bạn đã chấm xong. Điểm: %.2f", score);
        Notification notification = createNotification(
                studentId,
                "GRADED",
                "Đã chấm điểm",
                message
        );

        log.info("Sending graded notification to student {}", studentId);
        template.convertAndSendToUser(
                studentId.toString(),
                "/queue/notifications",
                toPayload(notification)
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getNotifications(Long userId) {
        return notificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toPayload)
                .toList();
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .ifPresent(notification -> {
                    notification.setRead(true);
                    notification.setReadAt(LocalDateTime.now());
                    notificationRepository.save(notification);
                });
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> notifications = notificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId);
        LocalDateTime now = LocalDateTime.now();
        for (Notification notification : notifications) {
            if (!notification.isRead()) {
                notification.setRead(true);
                notification.setReadAt(now);
            }
        }
        notificationRepository.saveAll(notifications);
    }

    private Notification createNotification(Long userId, String type, String title, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .isRead(false)
                .build();

        return notificationRepository.save(notification);
    }

    private Map<String, Object> toPayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", notification.getId());
        payload.put("type", notification.getType());
        payload.put("title", notification.getTitle());
        payload.put("message", notification.getMessage());
        payload.put("isRead", notification.isRead());
        payload.put("createdAt", notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : null);
        payload.put("readAt", notification.getReadAt() != null ? notification.getReadAt().toString() : null);
        return payload;
    }
}
