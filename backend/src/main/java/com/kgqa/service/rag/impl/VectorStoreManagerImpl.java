package com.kgqa.service.rag.impl;

import com.kgqa.service.rag.VectorStoreManager;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量存储管理服务实现
 * 负责向量存储的添加和搜索
 */
@Component
public class VectorStoreManagerImpl implements VectorStoreManager {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public VectorStoreManagerImpl(EmbeddingStore<TextSegment> embeddingStore,
                            EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<String> search(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .build();

        return embeddingStore.search(request).matches().stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.toList());
    }

    @Override
    public int addDocuments(List<String> chunks) {
        int count = 0;
        for (String chunk : chunks) {
            TextSegment segment = TextSegment.from(chunk);
            embeddingStore.add(embeddingModel.embed(chunk).content(), segment);
            count++;
        }
        return count;
    }
}
