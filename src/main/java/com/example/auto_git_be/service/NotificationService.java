package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.Notification;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.NotificationRepository;
import com.example.auto_git_be.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void notifyTeacherOnSubmission(Long teacherId, String studentName, String assignmentName, String className) {
        String message = String.format("Sinh viên %s vừa nộp bài cho bài tập %s lớp %s.", studentName, assignmentName, className);
        Notification notification = createNotification(
                teacherId,
                "SUBMIT",
                "Bài nộp mới",
            message,
            Map.of()
        );

        log.info("Sending submission to teacher {}", teacherId);
        template.convertAndSendToUser(
                teacherId.toString(),
                "/queue/notifications",
                toPayload(notification)
        );
    }

    @Transactional
    public void notifyStudentOnGraded(Long studentId, Double score, String assignmentCode, String classCode) {
        String message = String.format("Bài của bạn đã chấm xong. Điểm: %.2f", score);
        Map<String, String> metadata = new HashMap<>();
        if (assignmentCode != null && !assignmentCode.isBlank()) {
            metadata.put("assignmentCode", assignmentCode);
        }
        if (classCode != null && !classCode.isBlank()) {
            metadata.put("classCode", classCode);
        }

        Notification notification = createNotification(
                studentId,
                "GRADED",
                "Đã chấm điểm",
                message,
                metadata
        );

        log.info("Sending graded notification to student {}", studentId);
        template.convertAndSendToUser(
                studentId.toString(),
                "/queue/notifications",
                toPayload(notification)
        );
    }

    @Transactional
    public void notifyStudentOnTeacherComment(
            Long studentId,
            String teacherName,
            String assignmentTitle,
            String assignmentCode,
            String classCode,
            String targetBranch,
            String studentFilePath
    ) {
        String safeTeacherName = (teacherName == null || teacherName.isBlank()) ? "Giáo viên" : teacherName;
        String safeAssignmentTitle = (assignmentTitle == null || assignmentTitle.isBlank()) ? "bài tập" : assignmentTitle;
        String message = String.format("%s đã để lại nhận xét mới trong %s.", safeTeacherName, safeAssignmentTitle);

        Map<String, String> metadata = new HashMap<>();
        if (assignmentCode != null && !assignmentCode.isBlank()) {
            metadata.put("assignmentCode", assignmentCode);
        }
        if (classCode != null && !classCode.isBlank()) {
            metadata.put("classCode", classCode);
        }
        if (targetBranch != null && !targetBranch.isBlank()) {
            metadata.put("targetBranch", targetBranch);
        }
        if (studentFilePath != null && !studentFilePath.isBlank()) {
            metadata.put("studentFilePath", studentFilePath);
        }

        Notification notification = createNotification(
                studentId,
                "COMMENT",
                "Nhận xét mới từ giáo viên",
                message,
                metadata
        );

        log.info("Sending teacher-comment notification to student {}", studentId);
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

    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .ifPresent(notificationRepository::delete);
    }

    private Notification createNotification(
            Long userId,
            String type,
            String title,
            String message,
            Map<String, String> metadata
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(encodeMessage(message, metadata))
                .isRead(false)
                .build();

        return notificationRepository.save(notification);
    }

    private Map<String, Object> toPayload(Notification notification) {
        Map<String, Object> decoded = decodeMessage(notification.getMessage());
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", notification.getId());
        payload.put("type", notification.getType());
        payload.put("title", notification.getTitle());
        payload.put("message", decoded.getOrDefault("message", notification.getMessage()));
        payload.put("assignmentCode", decoded.get("assignmentCode"));
        payload.put("classCode", decoded.get("classCode"));
        payload.put("targetBranch", decoded.get("targetBranch"));
        payload.put("studentFilePath", decoded.get("studentFilePath"));
        payload.put("isRead", notification.isRead());
        payload.put("createdAt", notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : null);
        payload.put("readAt", notification.getReadAt() != null ? notification.getReadAt().toString() : null);
        return payload;
    }

    private String encodeMessage(String message, Map<String, String> metadata) {
        try {
            Map<String, Object> encoded = new HashMap<>();
            encoded.put("message", message);
            if (metadata != null && !metadata.isEmpty()) {
                encoded.putAll(metadata);
            }
            return objectMapper.writeValueAsString(encoded);
        } catch (Exception e) {
            log.warn("Failed to encode notification metadata, fallback to plain message: {}", e.getMessage());
            return message;
        }
    }

    private Map<String, Object> decodeMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return Map.of("message", "");
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(rawMessage, new TypeReference<>() {});
            if (parsed.containsKey("message")) {
                return parsed;
            }
        } catch (Exception ignored) {
        }

        return Map.of("message", rawMessage);
    }
}
