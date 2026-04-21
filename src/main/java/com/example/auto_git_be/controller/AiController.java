package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.ai.AiChatRequest;
import com.example.auto_git_be.dto.ai.WorkspaceUploadRequest;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.model.MessageType;
import com.example.auto_git_be.service.AiService;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.MessageService;
import com.example.auto_git_be.utils.Constant;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/ai")

public class AiController {

    private final AuthService authService;
    private final MessageService messageService;
    private final AiService aiService;

    @PostMapping("/ask")
    public ResponseEntity<String> askAiQuestion(
            @RequestBody AiChatRequest request,
            @RequestHeader("Authorization") String authHeader) throws JsonProcessingException {

        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);

        messageService.sendMessage(
                user.getId(),
                Constant.AI_ID,
                null,
                request.getMessage(),
                MessageType.AI_CHAT
        );

        aiService.generateAndStreamingResponse(user.getId(), user.getRole().name(),  request);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/upload-vector-db")
    public ResponseEntity<String> uploadVectorDb(
            @RequestBody WorkspaceUploadRequest request,
            @RequestHeader("Authorization") String authHeader
            ) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);

        aiService.uploadVectorDbAsync(user.getId(), request);

        return ResponseEntity.ok().build();
    }
}
