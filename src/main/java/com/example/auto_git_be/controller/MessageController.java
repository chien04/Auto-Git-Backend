package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.ChatMessageDTO;
import com.example.auto_git_be.dto.SendMessageRequest;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private AuthService authService;
    
    /**
     * Send a message (alternative REST endpoint)
     */
    @PostMapping("/send")
    public ResponseEntity<ChatMessageDTO> sendMessage(
            @RequestBody SendMessageRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User sender = authService.getUserFromToken(token);
            
            ChatMessageDTO message = messageService.sendMessage(
                    sender.getId(),
                    request.getReceiverId(),
                    request.getClassroomId(),
                    request.getContent(),
                    request.getType()
            );
            
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get private message history between two users
     */
    @GetMapping("/private/{otherUserId}")
    public ResponseEntity<List<ChatMessageDTO>> getPrivateMessages(
            @PathVariable Long otherUserId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User currentUser = authService.getUserFromToken(token);
            
            List<ChatMessageDTO> messages = messageService.getPrivateMessages(
                    currentUser.getId(),
                    otherUserId
            );
            
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get class group message history
     */
    @GetMapping("/class/{classroomId}")
    public ResponseEntity<List<ChatMessageDTO>> getClassMessages(
            @PathVariable Long classroomId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            authService.getUserFromToken(token); // Verify token
            
            List<ChatMessageDTO> messages = messageService.getClassMessages(classroomId);
            
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Mark message as read
     */
    @PostMapping("/{messageId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            authService.getUserFromToken(token); // Verify token
            
            messageService.markAsRead(messageId);
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Mark all messages from a sender as read
     */
    @PostMapping("/read/private/{senderId}")
    public ResponseEntity<Void> markPrivateMessagesAsRead(
            @PathVariable Long senderId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User receiver = authService.getUserFromToken(token);
            
            messageService.markPrivateMessagesAsRead(receiver.getId(), senderId);
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get unread message count
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);
            
            int count = messageService.getUnreadCount(user.getId());
            
            Map<String, Integer> response = new HashMap<>();
            response.put("count", count);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get unread message count from a specific sender
     */
    @GetMapping("/unread/count/from/{senderId}")
    public ResponseEntity<Map<String, Integer>> getUnreadPrivateCount(
            @PathVariable Long senderId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User receiver = authService.getUserFromToken(token);
            
            int count = messageService.getUnreadPrivateCount(receiver.getId(), senderId);
            
            Map<String, Integer> response = new HashMap<>();
            response.put("count", count);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get recent private chat conversations
     */
    @GetMapping("/recent-chats")
    public ResponseEntity<List<Map<String, Object>>> getRecentPrivateChats(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User currentUser = authService.getUserFromToken(token);
            
            List<Map<String, Object>> recentChats = messageService.getRecentPrivateChats(currentUser.getId());
            
            return ResponseEntity.ok(recentChats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
