package com.kgqa.service;

import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.model.entity.KnowledgeChunk;
import com.kgqa.repository.KnowledgeChunkRepository;
import com.kgqa.repository.KnowledgeRepository;
import com.kgqa.service.rag.DocumentLoader;
import com.kgqa.service.rag.TextSplitter;
import com.kgqa.service.rag.VectorStoreManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeProcessingService {
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final DocumentLoader documentLoader;
    private final TextSplitter textSplitter;
    private final VectorStoreManager vectorStoreManager;

    public KnowledgeProcessingService(KnowledgeRepository knowledgeRepository,
                                      KnowledgeChunkRepository chunkRepository,
                                      DocumentLoader documentLoader,
                                      TextSplitter textSplitter,
                                      VectorStoreManager vectorStoreManager) {
        this.knowledgeRepository = knowledgeRepository;
        this.chunkRepository = chunkRepository;
        this.documentLoader = documentLoader;
        this.textSplitter = textSplitter;
        this.vectorStoreManager = vectorStoreManager;
    }

    @Async
    public void processDocumentAsync(Long knowledgeId, byte[] fileBytes, String contentType) {
        try {
            processDocument(knowledgeId, fileBytes, contentType);
        } catch (Exception e) {
            vectorStoreManager.deleteByKnowledgeId(knowledgeId);
            KnowledgeBase knowledge = knowledgeRepository.selectById(knowledgeId);
            if (knowledge != null) {
                knowledge.setStatus("FAILED");
                knowledgeRepository.updateById(knowledge);
            }
        }
    }

    @Transactional
    public void processDocument(Long knowledgeId, byte[] fileBytes, String contentType) throws Exception {
        String content = documentLoader.loadText(fileBytes, contentType);
        List<String> chunks = textSplitter.split(content);

        vectorStoreManager.addDocuments(chunks, knowledgeId);

        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setKnowledgeId(knowledgeId);
            chunk.setContent(chunks.get(i));
            chunk.setChunkIndex(i);
            chunkRepository.insert(chunk);
        }

        KnowledgeBase knowledge = knowledgeRepository.selectById(knowledgeId);
        if (knowledge != null) {
            knowledge.setChunkCount(chunks.size());
            knowledge.setStatus("READY");
            knowledgeRepository.updateById(knowledge);
        }
    }
}
