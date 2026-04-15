package com.example.auto_git_be.controller;

import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getNotifications(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);
            return ResponseEntity.ok(notificationService.getNotifications(user.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long notificationId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);
            notificationService.markAsRead(user.getId(), notificationId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);
            notificationService.markAllAsRead(user.getId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
