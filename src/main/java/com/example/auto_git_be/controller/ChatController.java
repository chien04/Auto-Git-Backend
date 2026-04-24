package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.chat.ChatMessageDTO;
import com.example.auto_git_be.dto.chat.SendMessageRequest;
import com.example.auto_git_be.model.MessageType;
import com.example.auto_git_be.service.AiService;
import com.example.auto_git_be.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

	private final MessageService messageService;
	private final AiService aiService;
	private final SimpMessagingTemplate messagingTemplate;

	/**
	 * Private chat endpoint: /app/chat.private
	 * Sender/receiver subscribe to /user/queue/private
	 */
	@MessageMapping("/chat.private")
	public void sendPrivateMessage(@Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
			Long senderId = extractUserId(headerAccessor);

			if (request.getReceiverId() == null) {
				throw new RuntimeException("receiverId is required for private message");
			}

			ChatMessageDTO message = messageService.sendMessage(
					senderId,
					request.getReceiverId(),
					null,
					request.getContent(),
					MessageType.PRIVATE
			);

			// Send to receiver
			messagingTemplate.convertAndSendToUser(
					request.getReceiverId().toString(),
					"/queue/private",
					message
			);

			// Echo to sender so UI can sync with server state
			messagingTemplate.convertAndSendToUser(
					senderId.toString(),
					"/queue/private",
					message
			);
	}

	/**
	 * Group chat endpoint: /app/chat.class
	 * Users subscribe to /topic/class/{classroomId}
	 */
	@MessageMapping("/chat.class")
	public void sendClassMessage(@Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
			Long senderId = extractUserId(headerAccessor);

			if (request.getClassroomId() == null) {
				throw new RuntimeException("classroomId is required for class message");
			}

			ChatMessageDTO message = messageService.sendMessage(
					senderId,
					null,
					request.getClassroomId(),
					request.getContent(),
					MessageType.CLASS_GROUP
			);

			messagingTemplate.convertAndSend(
					"/topic/class/" + request.getClassroomId(),
					message
			);
	}

	private Long extractUserId(SimpMessageHeaderAccessor headerAccessor) {
		if (headerAccessor.getUser() == null || headerAccessor.getUser().getName() == null) {
			throw new RuntimeException("Unauthenticated websocket user");
		}
		return Long.parseLong(headerAccessor.getUser().getName());
	}
}


