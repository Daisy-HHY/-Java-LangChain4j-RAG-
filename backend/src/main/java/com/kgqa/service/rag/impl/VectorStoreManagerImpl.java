package com.kgqa.service.rag.impl;

import com.kgqa.model.dto.SourceItem;
import com.kgqa.service.rag.VectorStoreManager;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
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
    public List<SourceItem> searchWithScore(String query, int topK, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(minScore)
                .build();

        return embeddingStore.search(request).matches().stream()
                .map(match -> {
                    String text = match.embedded().text();
                    // match.score() returns cosine similarity (0-1)
                    double score = match.score() == null ? 0.0 : match.score();
                    return new SourceItem(text, score);
                })
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

    @Override
    public int addDocuments(List<String> chunks, Long knowledgeId) {
        int count = 0;
        Metadata metadata = new Metadata().put("knowledge_id", knowledgeId);
        for (String chunk : chunks) {
            TextSegment segment = TextSegment.from(chunk, metadata);
            embeddingStore.add(embeddingModel.embed(chunk).content(), segment);
            count++;
        }
        return count;
    }

    @Override
    public void deleteByKnowledgeId(Long knowledgeId) {
        var filter = MetadataFilterBuilder.metadataKey("knowledge_id").isEqualTo(knowledgeId);
        embeddingStore.removeAll(filter);
    }
}
