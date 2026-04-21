package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.ai.AiChatRequest;
import com.example.auto_git_be.dto.ai.FileContext;
import com.example.auto_git_be.dto.ai.FileDTO;
import com.example.auto_git_be.dto.ai.StudentAssignmentDTO;
import com.example.auto_git_be.dto.ai.WorkspaceUploadRequest;
import com.example.auto_git_be.entity.Message;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.model.MessageType;
import com.example.auto_git_be.repository.MessageRepository;
import com.example.auto_git_be.utils.Constant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ai4j.openai4j.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final StreamingChatLanguageModel chatModel;
    private final SimpMessagingTemplate template;
    private final MessageService messageService;
    private final FileCacheService fileCacheService;
    private final ChatHistoryCacheService chatHistoryCacheService;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public void generateAndStreamingResponse(Long userId, String role, AiChatRequest aiChatRequest) throws JsonProcessingException {

        List<ChatMessage> messages = new ArrayList<>();

        String systemPrompt = role.equals(User.UserRole.TEACHER.name())
                ? Constant.SYSTEM_PROMPT_TEACHER
                : Constant.SYSTEM_PROMPT_TUTOR;
        messages.add(new SystemMessage(systemPrompt));

        List<String> chatContext = getConversationContext(userId);
        if (chatContext != null && !chatContext.isEmpty()) {
            for (String json : chatContext) {
                JsonNode node = objectMapper.readTree(json);
                String msgRole = node.get("role").asText();
                String msgContent = node.get("content").asText();

                if ("user".equals(msgRole)) {
                    messages.add(new UserMessage(msgContent));
                } else {
                    messages.add(new AiMessage(msgContent));
                }
            }
        }
        String userQuestion = aiChatRequest.getMessage();
        StringBuilder currentPrompt = new StringBuilder();
        if (aiChatRequest.getFiles() != null && !aiChatRequest.getFiles().isEmpty()) {
            currentPrompt.append("Ngữ cảnh mã nguồn hiện tại:\n\n");
            for (FileContext file : aiChatRequest.getFiles()) {
                currentPrompt.append("<file name=\"").append(file.getFilename()).append("\">\n```\n")
                        .append(file.getFileContent())
                        .append("\n```\n</file>\n\n");
            }
        }
        currentPrompt.append("CÂU HỎI CỦA NGƯỜI DÙNG:\n").append(userQuestion);
        messages.add(new UserMessage(currentPrompt.toString()));

        chatHistoryCacheService.pushMessage(userId, "user", userQuestion);

        log.info("Bắt đầu xử lý AI cho User {}: {}", userId, aiChatRequest.getMessage());

        String destination = "/queue/ai-stream";
        chatModel.generate(messages, new StreamingResponseHandler<>() {

            private final StringBuilder fullAiResponse = new StringBuilder();
            @Override
            public void onNext(String s) {
                fullAiResponse.append(s);
                template.convertAndSendToUser(userId.toString(), destination, s);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    chatHistoryCacheService.pushMessage(userId, "assistant", fullAiResponse.toString());
                    template.convertAndSendToUser(userId.toString(), destination, "Done");
                    messageService.sendMessage(
                            Constant.AI_ID,
                            userId,
                            null,
                            response.content().text(),
                            MessageType.AI_CHAT
                    );
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Lỗi phản hồi {}: {}", userId, throwable.getMessage());
                template.convertAndSendToUser(userId.toString(), destination, "AI không phản hồi!");
            }
        });
    }

    public List<String> getConversationContext (Long userId) throws JsonProcessingException {
        List<String> messages = chatHistoryCacheService.getRecentMessages(userId);

        if (messages == null || messages.isEmpty()) {
            Pageable topTen = PageRequest.of(0, 10);
            List<Message> messageDB = messageRepository.findRecentMessageBetweenUsers(
                    userId,
                    Constant.AI_ID,
                    List.of(MessageType.AI_CHAT),
                    topTen);

            Collections.reverse(messageDB);
            List<String> result = new ArrayList<>();
            for (Message message : messageDB) {
                String role = message.getSender().getId().equals(userId) ? "user" : "Assistant";
                chatHistoryCacheService.pushMessage(userId, role, message.getContent());

                Map<String, String> msgMap = Map.of("role", role, "content", message.getContent());
                String json = objectMapper.writeValueAsString(msgMap);
                result.add(json);
            }
            return result;
        }
        return messages;
    }

    @Async
    public void uploadVectorDbAsync(Long userId, WorkspaceUploadRequest request) {
        if (request == null || request.getStudentAssignments() == null) {
            log.info("[AI] upload-vector-db skipped: empty payload for user {}", userId);
            return;
        }

        List<TextSegment> segmentsToEmbed = new ArrayList<>();

        String assignmentCode = request.getAssignmentCode();

        for (StudentAssignmentDTO sa : request.getStudentAssignments()) {
            String studentName =  sa.getStudentName();

            for (FileDTO f : sa.getFiles()) {
                String filename = f.getFileName();
                String content = f.getFileContent();
                String newHash = f.getHashcode();

                if(fileCacheService.isIdentical(userId, assignmentCode, studentName, filename, newHash)) {
                    continue;
                }

                String uniqueString = String.format("%d-%s-%s-%s", userId, assignmentCode, studentName, filename);
                String vectorId = UUID.nameUUIDFromBytes(uniqueString.getBytes()).toString();

                Metadata metadata = new Metadata();
                metadata.put("id", vectorId);
                metadata.put("embedded_by_id", userId.toString());
                metadata.put("assignment_code", assignmentCode);
                metadata.put("student_name", studentName);
                metadata.put("file_name", filename);
                metadata.put("file_hash", newHash);

                TextSegment segment = TextSegment.from(content, metadata);
                segmentsToEmbed.add(segment);

                fileCacheService.updateCache(userId, assignmentCode, studentName, filename, newHash);
            }
        }
        if (!segmentsToEmbed.isEmpty()) {
            log.info("[AI] Bắt đầu gọi API Embedding cho {} file bị thay đổi...", segmentsToEmbed.size());
            var embeddings = embeddingModel.embedAll(segmentsToEmbed).content();
            embeddingStore.addAll(embeddings, segmentsToEmbed);
            log.info("[AI] Upload lên Qdrant hoàn tất cho user {}!", userId);
        } else {
            log.info("[AI] Không có file nào thay đổi. Kết thúc luồng đồng bộ.");
        }
    }
}
