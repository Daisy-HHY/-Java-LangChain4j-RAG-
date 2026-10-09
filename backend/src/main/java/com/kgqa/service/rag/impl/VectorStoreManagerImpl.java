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

    private static final String KNOWLEDGE_ID_KEY = "knowledge_id";
    private static final String USER_ID_KEY = "user_id";
    private static final String VISIBILITY_KEY = "visibility";
    private static final String PRIVATE_VISIBILITY = "PRIVATE";
    private static final String PUBLIC_VISIBILITY = "PUBLIC";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public VectorStoreManagerImpl(EmbeddingStore<TextSegment> embeddingStore,
                            EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<String> search(String query, Long userId, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .filter(accessFilter(userId))
                .build();

        return embeddingStore.search(request).matches().stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.toList());
    }

    @Override
    public List<SourceItem> searchWithScore(String query, Long userId, int topK, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(minScore)
                .filter(accessFilter(userId))
                .build();

        return embeddingStore.search(request).matches().stream()
                .map(match -> {
                    TextSegment segment = match.embedded();
                    String text = segment.text();
                    // match.score() returns cosine similarity (0-1)
                    double score = match.score() == null ? 0.0 : match.score();
                    SourceItem source = new SourceItem(text, score);
                    Long knowledgeId = segment.metadata().getLong(KNOWLEDGE_ID_KEY);
                    if (knowledgeId != null) {
                        source.setKnowledgeId(String.valueOf(knowledgeId));
                    }
                    return source;
                })
                .collect(Collectors.toList());
    }

    @Override
    public int addDocuments(List<String> chunks, Long knowledgeId, Long userId) {
        requirePositiveId(knowledgeId, "knowledgeId");
        requirePositiveId(userId, "userId");
        return addSegments(chunks, privateMetadata(knowledgeId, userId));
    }

    @Override
    public int addSharedDocuments(List<String> chunks, Long knowledgeId) {
        requirePositiveId(knowledgeId, "knowledgeId");
        return addSegments(chunks, new Metadata()
                .put(KNOWLEDGE_ID_KEY, knowledgeId)
                .put(VISIBILITY_KEY, PUBLIC_VISIBILITY));
    }

    @Override
    public int replaceDocuments(List<String> chunks, Long knowledgeId, Long userId) {
        requirePositiveId(knowledgeId, "knowledgeId");
        requirePositiveId(userId, "userId");
        Metadata metadata = privateMetadata(knowledgeId, userId);
        List<TextSegment> segments = chunks.stream()
                .map(chunk -> TextSegment.from(chunk, metadata.copy()))
                .toList();
        // 在调用外部模型期间保留旧索引，避免模型失败时丢失已有向量。
        List<Embedding> embeddings = chunks.stream()
                .map(chunk -> embeddingModel.embed(chunk).content())
                .toList();
        deleteByKnowledgeId(knowledgeId);
        embeddingStore.addAll(embeddings, segments);
        return segments.size();
    }

    private Metadata privateMetadata(Long knowledgeId, Long userId) {
        return new Metadata()
                .put(KNOWLEDGE_ID_KEY, knowledgeId)
                .put(USER_ID_KEY, userId)
                .put(VISIBILITY_KEY, PRIVATE_VISIBILITY);
    }

    private int addSegments(List<String> chunks, Metadata metadata) {
        int count = 0;
        for (String chunk : chunks) {
            TextSegment segment = TextSegment.from(chunk, metadata.copy());
            embeddingStore.add(embeddingModel.embed(chunk).content(), segment);
            count++;
        }
        return count;
    }

    @Override
    public void deleteByKnowledgeId(Long knowledgeId) {
        requirePositiveId(knowledgeId, "knowledgeId");
        var filter = MetadataFilterBuilder.metadataKey(KNOWLEDGE_ID_KEY).isEqualTo(knowledgeId);
        embeddingStore.removeAll(filter);
    }

    /**
     * Restricts vector search to the current user's private documents and shared documents.
     * A null user ID is intentionally limited to shared documents instead of allowing an
     * unscoped/global search.
     */
    private Filter accessFilter(Long userId) {
        Filter sharedFilter = MetadataFilterBuilder.metadataKey(VISIBILITY_KEY)
                .isEqualTo(PUBLIC_VISIBILITY);
        if (userId == null) {
            return sharedFilter;
        }
        requirePositiveId(userId, "userId");
        Filter privateFilter = MetadataFilterBuilder.metadataKey(USER_ID_KEY)
                .isEqualTo(userId)
                .and(MetadataFilterBuilder.metadataKey(VISIBILITY_KEY)
                        .isEqualTo(PRIVATE_VISIBILITY));
        return privateFilter.or(sharedFilter);
    }

    private void requirePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(name + " must be a positive ID");
        }
    }
}
