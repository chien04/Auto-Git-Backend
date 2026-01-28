package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.ChatMessageDTO;
import com.example.auto_git_be.dto.SendMessageRequest;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.model.MessageType;
import com.example.auto_git_be.repository.UserRepository;
import com.example.auto_git_be.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SimpUserRegistry userRegistry;
    
    /**
     * Handle private messages
     * Endpoint: /app/chat.private
     */
    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        try {
            // Extract user email from session (set during handshake)
            String email = (String) headerAccessor.getSessionAttributes().get("userEmail");
            User sender = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            Long senderId = sender.getId();
            
            // Save message to database
            ChatMessageDTO message = messageService.sendMessage(
                    senderId,
                    request.getReceiverId(),
                    null,
                    request.getContent(),
                    MessageType.PRIVATE
            );
            
            // Debug: Check connected users
            for (SimpUser user : userRegistry.getUsers()) {
            }
            
            // Send to receiver via WebSocket
            String receiverIdStr = request.getReceiverId().toString();
            String receiverDest = "/user/" + receiverIdStr + "/queue/private";
            
            // Check if receiver is connected
            SimpUser receiverUser = userRegistry.getUser(receiverIdStr);
            if (receiverUser != null) {
            } else {
            }
            
            messagingTemplate.convertAndSendToUser(
                    receiverIdStr,
                    "/queue/private",
                    message
            );
            
            // Also send back to sender for real-time update
            String senderIdStr = senderId.toString();
            String senderDest = "/user/" + senderIdStr + "/queue/private";
            
            // Check if sender is connected
            SimpUser senderUser = userRegistry.getUser(senderIdStr);
            if (senderUser != null) {
            } else {
            }
            
            messagingTemplate.convertAndSendToUser(
                    senderIdStr,
                    "/queue/private",
                    message
            );
            
        } catch (Exception e) {
        }
    }
    
    /**
     * Handle class group messages
     * Endpoint: /app/chat.class
     */
    @MessageMapping("/chat.class")
    public void sendClassMessage(@Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        try {
            // Extract user email from session
            String email = (String) headerAccessor.getSessionAttributes().get("userEmail");
            User sender = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            Long senderId = sender.getId();
            
            // Save message to database
            ChatMessageDTO message = messageService.sendMessage(
                    senderId,
                    null,
                    request.getClassroomId(),
                    request.getContent(),
                    MessageType.CLASS_GROUP
            );
            
            // Broadcast to all users in the classroom
            messagingTemplate.convertAndSend(
                    "/topic/class/" + request.getClassroomId(),
                    message
            );
            
        } catch (Exception e) {
        }
    }
    
    /**
     * Handle user joining a chat room
     * Endpoint: /app/chat.join
     */
    @MessageMapping("/chat.join")
    public void joinChat(@Payload Long classroomId, SimpMessageHeaderAccessor headerAccessor) {
        try {
            String email = (String) headerAccessor.getSessionAttributes().get("userEmail");
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            Long userId = user.getId();
            
            // Notify others that user joined (optional)
            messagingTemplate.convertAndSend(
                    "/topic/class/" + classroomId + "/join",
                    "User " + userId + " joined"
            );
            
        } catch (Exception e) {
        }
    }
}

