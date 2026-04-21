package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.ai.FileContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QdrantService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public void uploadFile(Long userId, String workspace, FileContext fileContext) {
        Metadata metadata = new Metadata();
        

    }
}
