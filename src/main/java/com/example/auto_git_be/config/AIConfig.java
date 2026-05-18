package com.example.auto_git_be.config;

import com.example.auto_git_be.tool.DatabaseQueryTool;
import com.example.auto_git_be.tool.VectorQueryTool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.example.auto_git_be.tool.TeacherAiService;
@Configuration
public class AIConfig {

    @Value("${openai.api.key}")
    private String openAiKey;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("bge-m3")
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> qdrantStore() {
        return QdrantEmbeddingStore.builder()
                .host("localhost")
                .port(6334)
                .collectionName("ai_chat")
                .build();
    }

    @Bean
    public StreamingChatLanguageModel chatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(openAiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.0)
                .build();
    }

    @Bean
    public ChatLanguageModel normalChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.0)
                .build();
    }

    @Bean
    public ChatLanguageModel jsonChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.0)
                .responseFormat("json_object")
                .build();
    }

    @Bean
    public TeacherAiService teacherAiService(StreamingChatLanguageModel streamingChatModel, DatabaseQueryTool databaseQueryTool, VectorQueryTool vectorQueryTool) {
        return AiServices.builder(TeacherAiService.class)
                .streamingChatLanguageModel(streamingChatModel)
                .tools(databaseQueryTool, vectorQueryTool)
                .build();
    }
}
