package com.example.auto_git_be.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    @Value("${openai.api.key}")
    private String openAiKey;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("qwen3-embedding:0.6b")
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
                .build();
    }
}
