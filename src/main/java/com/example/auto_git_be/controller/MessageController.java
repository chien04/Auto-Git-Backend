package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.chat.ChatMessageDTO;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.service.MessageService;
import com.example.auto_git_be.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

	private final MessageService messageService;
	private final AuthService authService;

	@GetMapping("/private/{otherUserId}")
	public ResponseEntity<List<ChatMessageDTO>> getPrivateMessages(
			@PathVariable Long otherUserId,
			@RequestHeader("Authorization") String authHeader) {
			String token = authHeader.substring(7);
			User currentUser = authService.getUserFromToken(token);
			return ResponseEntity.ok(messageService.getPrivateMessages(currentUser.getId(), otherUserId));
	}

	@GetMapping("/class/{classroomId}")
	public ResponseEntity<List<ChatMessageDTO>> getClassMessages(
			@PathVariable Long classroomId,
			@RequestHeader("Authorization") String authHeader) {
		try {
			String token = authHeader.substring(7);
			authService.getUserFromToken(token); // authentication check
			return ResponseEntity.ok(messageService.getClassMessages(classroomId));
		} catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}

	@GetMapping("/recent-chats")
	public ResponseEntity<List<Map<String, Object>>> getRecentPrivateChats(
			@RequestHeader("Authorization") String authHeader) {
		try {
			String token = authHeader.substring(7);
			User currentUser = authService.getUserFromToken(token);
			return ResponseEntity.ok(messageService.getRecentPrivateChats(currentUser.getId()));
		} catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}

	@PostMapping("/{messageId}/read")
	public ResponseEntity<Void> markAsRead(
			@PathVariable Long messageId,
			@RequestHeader("Authorization") String authHeader) {
		try {
			String token = authHeader.substring(7);
			User currentUser = authService.getUserFromToken(token);
			messageService.markAsRead(messageId, currentUser.getId());
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}
}

