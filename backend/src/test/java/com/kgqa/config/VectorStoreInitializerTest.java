package com.kgqa.config;

import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.model.entity.KnowledgeChunk;
import com.kgqa.repository.KnowledgeChunkRepository;
import com.kgqa.repository.KnowledgeRepository;
import com.kgqa.service.rag.VectorStoreManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VectorStoreInitializerTest {
    @Test
    void rebuildPreservesOwnerThroughScopedReplacement() {
        var knowledgeRepository = mock(KnowledgeRepository.class);
        var chunks = mock(KnowledgeChunkRepository.class);
        var vectors = mock(VectorStoreManager.class);
        var initializer = new VectorStoreInitializer(knowledgeRepository, chunks, vectors);
        ReflectionTestUtils.setField(initializer, "reindexOnStartup", true);
        var knowledge = new KnowledgeBase();
        knowledge.setId(11L);
        knowledge.setUserId(7L);
        var chunk = new KnowledgeChunk();
        chunk.setContent("private content");
        when(knowledgeRepository.selectList(any())).thenReturn(List.of(knowledge));
        when(chunks.selectList(any())).thenReturn(List.of(chunk));
        when(vectors.replaceDocuments(List.of("private content"), 11L, 7L)).thenReturn(1);

        initializer.run(new DefaultApplicationArguments());

        verify(vectors).replaceDocuments(List.of("private content"), 11L, 7L);
        verifyNoMoreInteractions(vectors);
    }

    @Test
    void unknownOwnerIsNeverAutomaticallyPublishedOrDeleted() {
        var knowledgeRepository = mock(KnowledgeRepository.class);
        var chunks = mock(KnowledgeChunkRepository.class);
        var vectors = mock(VectorStoreManager.class);
        var initializer = new VectorStoreInitializer(knowledgeRepository, chunks, vectors);
        ReflectionTestUtils.setField(initializer, "reindexOnStartup", true);
        var knowledge = new KnowledgeBase();
        knowledge.setId(11L);
        when(knowledgeRepository.selectList(any())).thenReturn(List.of(knowledge));

        initializer.run(new DefaultApplicationArguments());

        verifyNoInteractions(chunks, vectors);
    }

    @Test
    void disabledReindexDoesNotTouchPersistedData() {
        var knowledgeRepository = mock(KnowledgeRepository.class);
        var chunks = mock(KnowledgeChunkRepository.class);
        var vectors = mock(VectorStoreManager.class);
        var initializer = new VectorStoreInitializer(knowledgeRepository, chunks, vectors);
        initializer.run(new DefaultApplicationArguments());
        verifyNoInteractions(knowledgeRepository, chunks, vectors);
    }
}
