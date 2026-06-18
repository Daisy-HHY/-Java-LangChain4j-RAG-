package com.kgqa.service.rag.impl;

import com.kgqa.model.dto.SourceItem;
import com.kgqa.service.rag.VectorStoreManager;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
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
        return searchWithScore(query, topK, minScore, null);
    }

    @Override
    public List<SourceItem> searchWithScore(String query, int topK, double minScore, Long userId) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(minScore);

        if (userId != null) {
            requestBuilder.filter(MetadataFilterBuilder.metadataKey("user_id").isEqualTo(userId));
        }

        EmbeddingSearchRequest request = requestBuilder.build();

        return embeddingStore.search(request).matches().stream()
                .map(match -> {
                    TextSegment segment = match.embedded();
                    String text = segment.text();
                    // match.score() returns cosine similarity (0-1)
                    double score = match.score() == null ? 0.0 : match.score();
                    SourceItem source = new SourceItem(text, score);
                    source.setSourceType(getStringMetadata(segment.metadata(), "source_type", "DOCUMENT"));
                    Long knowledgeId = segment.metadata().getLong("knowledge_id");
                    if (knowledgeId != null) {
                        source.setKnowledgeId(String.valueOf(knowledgeId));
                    }
                    Long matchedUserId = segment.metadata().getLong("user_id");
                    if (matchedUserId != null) {
                        source.setUserId(String.valueOf(matchedUserId));
                    }
                    source.setDocumentTitle(getStringMetadata(segment.metadata(), "document_title", null));
                    Long chunkIndex = segment.metadata().getLong("chunk_index");
                    if (chunkIndex != null) {
                        source.setChunkIndex(chunkIndex.intValue());
                    }
                    return source;
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
        return addDocuments(chunks, knowledgeId, null);
    }

    @Override
    public int addDocuments(List<String> chunks, Long knowledgeId, Long userId) {
        return addDocuments(chunks, knowledgeId, userId, null);
    }

    @Override
    public int addDocuments(List<String> chunks, Long knowledgeId, Long userId, String documentTitle) {
        int count = 0;
        Metadata metadata = new Metadata().put("knowledge_id", knowledgeId);
        if (userId != null) {
            metadata.put("user_id", userId);
        }
        if (documentTitle != null && !documentTitle.isBlank()) {
            metadata.put("document_title", documentTitle);
        }
        metadata.put("source_type", "DOCUMENT");
        for (int i = 0; i < chunks.size(); i++) {
            Metadata chunkMetadata = metadata.copy().put("chunk_index", i);
            TextSegment segment = TextSegment.from(chunks.get(i), chunkMetadata);
            embeddingStore.add(embeddingModel.embed(chunks.get(i)).content(), segment);
            count++;
        }
        return count;
    }

    @Override
    public void deleteByKnowledgeId(Long knowledgeId) {
        var filter = MetadataFilterBuilder.metadataKey("knowledge_id").isEqualTo(knowledgeId);
        embeddingStore.removeAll(filter);
    }

    @Override
    public void deleteByKnowledgeId(Long knowledgeId, Long userId) {
        if (userId == null) {
            deleteByKnowledgeId(knowledgeId);
            return;
        }

        Filter knowledgeFilter = MetadataFilterBuilder.metadataKey("knowledge_id").isEqualTo(knowledgeId);
        Filter userFilter = MetadataFilterBuilder.metadataKey("user_id").isEqualTo(userId);
        embeddingStore.removeAll(knowledgeFilter.and(userFilter));
    }

    private String getStringMetadata(Metadata metadata, String key, String defaultValue) {
        try {
            String value = metadata.getString(key);
            return value == null || value.isBlank() ? defaultValue : value;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
