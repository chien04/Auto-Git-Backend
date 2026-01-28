package com.example.auto_git_be.dto;

import com.example.auto_git_be.model.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequest {
    private Long receiverId;  // Required for PRIVATE messages
    private Long classroomId; // Required for CLASS_GROUP messages
    private String content;
    private MessageType type;
}
