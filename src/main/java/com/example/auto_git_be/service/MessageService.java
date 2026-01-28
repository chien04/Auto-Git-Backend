package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.ChatMessageDTO;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Message;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.model.MessageType;
import com.example.auto_git_be.repository.ClassRoomRepository;
import com.example.auto_git_be.repository.MessageRepository;
import com.example.auto_git_be.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ClassRoomRepository classRoomRepository;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * Send a message
     */
    @Transactional
    public ChatMessageDTO sendMessage(Long senderId, Long receiverId, Long classroomId, 
                                     String content, MessageType type) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        
        Message.MessageBuilder messageBuilder = Message.builder()
                .sender(sender)
                .content(content)
                .type(type)
                .isRead(false);
        
        // Set receiver for PRIVATE messages
        if (type == MessageType.PRIVATE && receiverId != null) {
            User receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new RuntimeException("Receiver not found"));
            messageBuilder.receiver(receiver);
        }
        
        // Set classroom for CLASS_GROUP messages
        if (type == MessageType.CLASS_GROUP && classroomId != null) {
            ClassRoom classRoom = classRoomRepository.findById(classroomId)
                    .orElseThrow(() -> new RuntimeException("Classroom not found"));
            messageBuilder.classRoom(classRoom);
        }
        
        Message message = messageRepository.save(messageBuilder.build());
        return convertToDTO(message);
    }
    
    /**
     * Get private messages between two users
     */
    public List<ChatMessageDTO> getPrivateMessages(Long userId1, Long userId2) {
        List<Message> messages = messageRepository.findPrivateMessages(userId1, userId2);
        return messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Get class group messages
     */
    public List<ChatMessageDTO> getClassMessages(Long classroomId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new RuntimeException("Classroom not found"));
        
        List<Message> messages = messageRepository.findByClassRoomAndTypeOrderByCreatedAtAsc(
                classRoom, MessageType.CLASS_GROUP);
        
        return messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Mark message as read
     */
    @Transactional
    public void markAsRead(Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        message.setIsRead(true);
        messageRepository.save(message);
        
        // Broadcast read receipt to sender via WebSocket
        ChatMessageDTO dto = new ChatMessageDTO(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.getReceiver() != null ? message.getReceiver().getId() : null,
                message.getClassRoom() != null ? message.getClassRoom().getId() : null,
                message.getContent(),
                message.getCreatedAt(),
                message.getType(),
                message.getIsRead()
        );
        
        // Send to sender so they see the read receipt (✓✓)
        messagingTemplate.convertAndSendToUser(
                message.getSender().getId().toString(),
                "/queue/private",
                dto
        );
    }
    
    /**
     * Mark all messages from a sender as read
     */
    @Transactional
    public void markPrivateMessagesAsRead(Long receiverId, Long senderId) {
        List<Message> messages = messageRepository.findPrivateMessages(receiverId, senderId);
        messages.stream()
                .filter(m -> m.getReceiver() != null && m.getReceiver().getId().equals(receiverId))
                .filter(m -> !m.getIsRead())
                .forEach(m -> {
                    m.setIsRead(true);
                    messageRepository.save(m);
                });
    }
    
    /**
     * Get unread message count for a user
     */
    public int getUnreadCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return messageRepository.countByReceiverAndIsReadFalse(user);
    }
    
    /**
     * Get unread private message count from a specific sender
     */
    public int getUnreadPrivateCount(Long receiverId, Long senderId) {
        return messageRepository.countUnreadPrivateMessages(receiverId, senderId);
    }
    
    /**
     * Get recent private chat conversations for a user
     */
    public List<Map<String, Object>> getRecentPrivateChats(Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Get ALL private messages involving this user (both sent and received)
        @SuppressWarnings("unchecked")
        List<Message> allMessages = messageRepository.findAll().stream()
                .filter(m -> m.getType() == MessageType.PRIVATE)
                .filter(m -> {
                    boolean isSender = m.getSender().getId().equals(userId);
                    boolean isReceiver = m.getReceiver() != null && m.getReceiver().getId().equals(userId);
                    return isSender || isReceiver;
                })
                .collect(java.util.stream.Collectors.toList());
        

        // Group by other user and get the LAST message in each conversation
        Map<Long, Message> lastMessages = new java.util.HashMap<>();
        for (Message msg : allMessages) {
            Long otherUserId;
            if (msg.getSender().getId().equals(userId)) {
                otherUserId = msg.getReceiver() != null ? msg.getReceiver().getId() : null;
            } else {
                otherUserId = msg.getSender().getId();
            }
            
            if (otherUserId != null) {
                if (!lastMessages.containsKey(otherUserId) || 
                    msg.getCreatedAt().isAfter(lastMessages.get(otherUserId).getCreatedAt())) {
                    lastMessages.put(otherUserId, msg);
                }
            }
        }
        
        // Convert to list of maps
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map.Entry<Long, Message> entry : lastMessages.entrySet()) {
            Message lastMsg = entry.getValue();
            User otherUser = lastMsg.getSender().getId().equals(userId) 
                ? lastMsg.getReceiver() 
                : lastMsg.getSender();
            
            Map<String, Object> chat = new java.util.HashMap<>();
            chat.put("userId", otherUser.getId());
            chat.put("userName", otherUser.getName());
            chat.put("userEmail", otherUser.getEmail());
            chat.put("lastMessage", lastMsg.getContent());
            chat.put("lastMessageTime", lastMsg.getCreatedAt());
            chat.put("unreadCount", getUnreadPrivateCount(userId, otherUser.getId()));
            result.add(chat);
        }
        
        // Sort by last message time descending
        result.sort((a, b) -> {
            java.time.LocalDateTime timeA = (java.time.LocalDateTime) a.get("lastMessageTime");
            java.time.LocalDateTime timeB = (java.time.LocalDateTime) b.get("lastMessageTime");
            return timeB.compareTo(timeA);
        });
        
        return result;
    }
    
    /**
     * Convert Message entity to DTO
     */
    private ChatMessageDTO convertToDTO(Message message) {
        return ChatMessageDTO.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getName())
                .receiverId(message.getReceiver() != null ? message.getReceiver().getId() : null)
                .receiverName(message.getReceiver() != null ? message.getReceiver().getName() : null)
                .classroomId(message.getClassRoom() != null ? message.getClassRoom().getId() : null)
                .content(message.getContent())
                .type(message.getType())
                .isRead(message.getIsRead())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
