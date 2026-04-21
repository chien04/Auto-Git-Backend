package com.example.auto_git_be.dto.chat;

import com.example.auto_git_be.model.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {
    private Long id;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private Long classroomId;
    private String content;
    private MessageType type;
    private Boolean isRead;
    private LocalDateTime createdAt;
}

