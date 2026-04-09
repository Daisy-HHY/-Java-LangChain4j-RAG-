package com.kgqa.data;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化入库器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingIngestor {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 批量向量化 MedQA 记录并写入 EmbeddingStore
     */
    public void ingestMedQaBatch(List<MedQaRecord> records, int batchIndex) {
        List<TextSegment> segments = new ArrayList<>();

        for (MedQaRecord record : records) {
            TextSegment segment = TextSegment.from(record.toEmbeddingText());
            segments.add(segment);
        }

        // 逐条 embedding（LangChain4j 1.x 的 embedAll 批量接口较慢）
        List<Embedding> embeddings = new ArrayList<>();
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            embeddings.add(embedding);
        }

        // 写入 EmbeddingStore
        embeddingStore.addAll(embeddings, segments);

        log.info("批次 {} 向量化完成，写入 {} 条", batchIndex, segments.size());
    }
}
