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
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/ai")

public class AiController {

    private final AuthService authService;
    private final MessageService messageService;
    private final AiService aiService;
    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrantClient;

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

        if("STUDENT".equals(user.getRole().name())){
            aiService.generateStudentStreamingResponse(user.getId(), request);
        }

        else aiService.generateTeacherStreamingResponse(user.getId(), request);
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

    @GetMapping("/test-vector-score")
    public List<String> testScore(@RequestParam String query) throws Exception {
        float[] vectorArray = embeddingModel.embed(query).content().vector();
        List<Float> vector = new ArrayList<>();
        for (float v : vectorArray) vector.add(v);

        List<Points.ScoredPoint> points = qdrantClient.searchAsync(
                Points.SearchPoints.newBuilder()
                        .setCollectionName("ai_chat")
                        .addAllVector(vector)
                        .setLimit(5)
                        .setScoreThreshold(0.0f)
                        .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                        .build()
        ).get();

        return points.stream()
                .map(p -> String.format("Score: %.4f | %s",
                        p.getScore(),
                        p.getPayloadMap().get("student_name").getStringValue()))
                .collect(Collectors.toList());
    }
}
