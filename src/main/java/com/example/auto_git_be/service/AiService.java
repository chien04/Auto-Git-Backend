package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.ai.AiChatRequest;
import com.example.auto_git_be.dto.ai.FileContext;
import com.example.auto_git_be.dto.ai.FileDTO;
import com.example.auto_git_be.dto.ai.StudentAssignmentDTO;
import com.example.auto_git_be.dto.ai.WorkspaceUploadRequest;
import com.example.auto_git_be.entity.Message;
import com.example.auto_git_be.model.MessageType;
import com.example.auto_git_be.repository.MessageRepository;
import com.example.auto_git_be.utils.Constant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.example.auto_git_be.tool.TeacherAiService;

import java.util.*;

@Slf4j
@Service
public class AiService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final ChatLanguageModel normalChatModel;
    private final ChatLanguageModel jsonChatModel;
    private final SimpMessagingTemplate template;
    private final MessageService messageService;
    private final FileCacheService fileCacheService;
    private final ChatHistoryCacheService chatHistoryCacheService;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final TeacherAiService teacherAiService;

    public AiService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            StreamingChatLanguageModel streamingChatLanguageModel,
            @Qualifier("normalChatModel") ChatLanguageModel normalChatModel,
            @Qualifier("jsonChatModel") ChatLanguageModel jsonChatModel,
            SimpMessagingTemplate template,
            MessageService messageService,
            FileCacheService fileCacheService,
            ChatHistoryCacheService chatHistoryCacheService,
            MessageRepository messageRepository,
            ObjectMapper objectMapper,
            TeacherAiService teacherAiService
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.normalChatModel = normalChatModel;
        this.jsonChatModel = jsonChatModel;
        this.template = template;
        this.messageService = messageService;
        this.fileCacheService = fileCacheService;
        this.chatHistoryCacheService = chatHistoryCacheService;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.teacherAiService = teacherAiService;
    }

    public void generateStudentStreamingResponse(Long userId, AiChatRequest aiChatRequest) throws JsonProcessingException {
        log.info("Bắt đầu xử lý AI cho Học sinh {}: {}", userId, aiChatRequest.getMessage());

        String systemPrompt = Constant.SYSTEM_PROMPT_TUTOR;
        List<ChatMessage> messages = buildChatMessages(userId, systemPrompt, aiChatRequest);

        chatHistoryCacheService.pushMessage(userId, "user", aiChatRequest.getMessage());

        streamingChatLanguageModel.chat(messages, createStreamingHandler(userId));
    }

    public void generateTeacherStreamingResponse(Long userId, AiChatRequest aiChatRequest) throws JsonProcessingException {
        log.info("Bắt đầu xử lý AI cho Giáo viên {}: Mã bài tập {}", userId, aiChatRequest.getAssignmentCode());

        String userQuestion = aiChatRequest.getMessage();
        chatHistoryCacheService.pushMessage(userId, "user", userQuestion);

        StringBuilder fullContextMessage = new StringBuilder();
        StringBuilder chatHistoryBuilder = new StringBuilder();
        List<String> chatContext = getConversationContext(userId);
        if (chatContext != null && !chatContext.isEmpty()) {
            fullContextMessage.append("---  CHAT HISTORY ---\n");
            for (String json : chatContext) {
                JsonNode node = objectMapper.readTree(json);
                String role = node.get("role").asText();
                String content = node.get("content").asText();
                fullContextMessage.append(role).append(": ").append(content).append("\n");
                chatHistoryBuilder.append(role).append(": ").append(content).append("\n");
            }
            fullContextMessage.append("--------------------------\n\n");
        }

        String rewrittenQuery = rewriteQueryForTeacher(userQuestion, chatHistoryBuilder.toString());
        log.info("[AI] Rewritten query: {}", rewrittenQuery);

        if (aiChatRequest.getFiles() != null && !aiChatRequest.getFiles().isEmpty()) {
            fullContextMessage.append("Current source code context:\n");
            for (FileContext file : aiChatRequest.getFiles()) {
                fullContextMessage.append("<file name=\"").append(file.getFilename()).append("\">\n```\n")
                        .append(file.getFileContent())
                        .append("\n```\n</file>\n\n");
            }
        }

        fullContextMessage.append("USER'S ORIGINAL QUESTION:\n").append(userQuestion).append("\n\n");
        fullContextMessage.append("SYSTEM REWRITTEN QUERY INFO:\n").append(rewrittenQuery).append("\n");

        String destination = "/queue/ai-stream";

        TokenStream tokenStream = teacherAiService.chat(
                Constant.SYSTEM_PROMPT_TEACHER,
                aiChatRequest.getAssignmentCode(),
                fullContextMessage.toString()
        );

        tokenStream
                .onPartialResponse(token -> {
                    template.convertAndSendToUser(userId.toString(), destination, token);
                })
                .onCompleteResponse(response -> {
                    try {
                        String fullResponse = response.aiMessage().text();
                        chatHistoryCacheService.pushMessage(userId, "assistant", fullResponse);
                        template.convertAndSendToUser(userId.toString(), destination, "Done");
                        messageService.sendMessage(Constant.AI_ID, userId, null, fullResponse, MessageType.AI_CHAT);
                    } catch (Exception e) {
                        log.error("Lỗi khi lưu kết quả chat: ", e);
                    }
                })
                .onError(error -> {
                    log.error("Lỗi quá trình stream AI (Giáo viên): ", error);
                    template.convertAndSendToUser(userId.toString(), destination, "AI gặp lỗi truy xuất dữ liệu.");
                })
                .start();
    }

    private String rewriteQueryForTeacher(String userQuestion, String chatHistory) {
        try {
            String userPrompt = "Chat history:\\n" + chatHistory + "\\n\\nLatest user question: " + userQuestion;

            ChatResponse response = normalChatModel.chat(
                    SystemMessage.from(Constant.REWRITE_PROMPT),
                    UserMessage.from(userPrompt)
            );
            return response.aiMessage().text();
        } catch (Exception e) {
            log.error("Lỗi khi rewrite query", e);
            return userQuestion;
        }
    }

    private List<ChatMessage> buildChatMessages(Long userId, String systemPrompt, AiChatRequest aiChatRequest) throws JsonProcessingException {
        List<ChatMessage> messages = new ArrayList<>();
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
            currentPrompt.append("Current source code context:\n\n");
            for (FileContext file : aiChatRequest.getFiles()) {
                currentPrompt.append("<file name=\"").append(file.getFilename()).append("\">\n```\n")
                        .append(file.getFileContent())
                        .append("\n```\n</file>\n\n");
            }
        }
        currentPrompt.append("User's question:\n").append(userQuestion);
        messages.add(new UserMessage(currentPrompt.toString()));

        return messages;
    }

    private StreamingChatResponseHandler createStreamingHandler(Long userId) {
        String destination = "/queue/ai-stream";

        return new StreamingChatResponseHandler() {
            private final StringBuilder fullAiResponse = new StringBuilder();

            @Override
            public void onPartialResponse(String partialResponse) {
                fullAiResponse.append(partialResponse);
                template.convertAndSendToUser(userId.toString(), destination, partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                try {
                    chatHistoryCacheService.pushMessage(userId, "assistant", fullAiResponse.toString());
                    template.convertAndSendToUser(userId.toString(), destination, "Done");
                    messageService.sendMessage(
                            Constant.AI_ID,
                            userId,
                            null,
                            response.aiMessage().text(),
                            MessageType.AI_CHAT
                    );
                } catch (JsonProcessingException e) {
                    log.error("Lỗi parse JSON khi lưu lịch sử: ", e);
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Lỗi phản hồi {}: {}", userId, throwable.getMessage());
                template.convertAndSendToUser(userId.toString(), destination, "AI không phản hồi!");
            }
        };
    }

    public List<String> getConversationContext(Long userId) throws JsonProcessingException {
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
            String studentName = sa.getStudentName();

            List<Map<String, String>> filesBatch = new ArrayList<>();

            for (FileDTO f : sa.getFiles()) {
                String taskOrderNo = String.valueOf(f.getTaskOrderNo());
                String filename = f.getFileName();
                String content = f.getFileContent();

                if (content == null || content.trim().isEmpty()) {
                    continue;
                }

                if (fileCacheService.isIdentical(userId, assignmentCode, studentName, taskOrderNo, f.getHashcode())) {
                    continue;
                }

                Map<String, String> fileData = new HashMap<>();
                fileData.put("file_name", filename);
                fileData.put("content", content);

                filesBatch.add(fileData);
            }

            if (filesBatch.isEmpty()) {
                continue;
            }

            String llmJsonResponse = "[]";
            try {
                String inputJson = objectMapper.writeValueAsString(filesBatch);

                String systemPrompt = Constant.SYSTEM_PROMPT_SUMMARY;

                String userPrompt = "Dữ liệu các file:\n" + inputJson;

                log.info("[AI] Đang gọi LLM tóm tắt {} file cho sinh viên: {}", filesBatch.size(), studentName);

                ChatResponse response = jsonChatModel.chat(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                );

                String rawText = response.aiMessage().text();

                int startIndex = rawText.indexOf('[');
                int endIndex = rawText.lastIndexOf(']');

                if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    llmJsonResponse = rawText.substring(startIndex, endIndex + 1);
                } else {
                    llmJsonResponse = rawText;
                }

            } catch (Exception e) {
                log.error("[AI] Lỗi khi gọi LLM tóm tắt file cho sinh viên {}", studentName, e);
            }

            Map<String, String> summaryMap = new HashMap<>();
            try {
                JsonNode summariesNode = objectMapper.readTree(llmJsonResponse);
                if (summariesNode.isArray()) {
                    for (JsonNode node : summariesNode) {
                        if (node.has("file_name") && node.has("summary")) {
                            summaryMap.put(node.get("file_name").asText(), node.get("summary").asText());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[AI] Lỗi parse JSON từ LLM", e);
            }

            for (FileDTO f : sa.getFiles()) {
                String filename = f.getFileName();
                String taskOrderNo = String.valueOf(f.getTaskOrderNo());

                String textToEmbed = summaryMap.getOrDefault(filename, f.getFileContent());

                String vectorUniqueStr = String.format("%d-%s-%s-%s", userId, assignmentCode, studentName, taskOrderNo);
                String vectorId = UUID.nameUUIDFromBytes(vectorUniqueStr.getBytes()).toString();

                Metadata metadata = new Metadata();
                metadata.put("id", vectorId);
                metadata.put("embedded_by_id", userId.toString());
                metadata.put("assignment_code", assignmentCode);
                metadata.put("student_name", studentName);
                metadata.put("task_order_no", taskOrderNo);
                metadata.put("file_name", filename);
                metadata.put("file_hash", f.getHashcode());
                metadata.put("raw_source_code", f.getFileContent());

                segmentsToEmbed.add(TextSegment.from(textToEmbed, metadata));
                fileCacheService.updateCache(userId, assignmentCode, studentName, taskOrderNo, f.getHashcode());
            }
        }

        if (!segmentsToEmbed.isEmpty()) {
            log.info("EmbeddingModel class: {}", embeddingModel.getClass().getSimpleName());
            log.info("[AI] Bắt đầu gọi API Embedding cho {} file bị thay đổi (đã tóm tắt)...", segmentsToEmbed.size());
            var embeddings = embeddingModel.embedAll(segmentsToEmbed).content();

            List<Embedding> validEmbeddings = new ArrayList<>();
            List<TextSegment> validSegments = new ArrayList<>();

            for (int i = 0; i < embeddings.size(); i++) {
                var emb = embeddings.get(i);

                if (emb == null || emb.vector() == null || emb.vector().length == 0) {
                    log.error("Embedding rỗng tại index {} - file: {}", i,
                            segmentsToEmbed.get(i).metadata().getString("file_name"));
                    continue;
                }

                validEmbeddings.add(emb);
                validSegments.add(segmentsToEmbed.get(i));
            }

            if (!validEmbeddings.isEmpty()) {
                embeddingStore.addAll(validEmbeddings, validSegments);
            }
            log.info("[AI] Upload lên Qdrant hoàn tất cho user {}!", userId);
        } else {
            log.info("[AI] Không có file nào thay đổi. Kết thúc luồng đồng bộ.");
        }
    }
}