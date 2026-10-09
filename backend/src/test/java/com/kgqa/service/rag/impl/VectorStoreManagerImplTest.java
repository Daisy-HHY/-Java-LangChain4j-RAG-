package com.kgqa.service.rag.impl;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorStoreManagerImplTest {
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;
    @Mock
    private EmbeddingModel embeddingModel;
    private VectorStoreManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new VectorStoreManagerImpl(embeddingStore, embeddingModel);
    }

    private void stubEmbedding() {
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[]{1.0f, 0.0f})));
    }

    private void stubSearch() {
        stubEmbedding();
        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));
    }

    private EmbeddingSearchRequest capturedRequest() {
        var captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore).search(captor.capture());
        return captor.getValue();
    }

    @Test
    void privateDocumentsContainOwnerMetadata() {
        stubEmbedding();
        manager.addDocuments(List.of("private content"), 11L, 7L);
        var captor = ArgumentCaptor.forClass(TextSegment.class);
        verify(embeddingStore).add(any(Embedding.class), captor.capture());
        var segment = captor.getValue();
        assertEquals("private content", segment.text());
        assertEquals(11L, segment.metadata().getLong("knowledge_id"));
        assertEquals(7L, segment.metadata().getLong("user_id"));
        assertEquals("PRIVATE", segment.metadata().getString("visibility"));
    }

    @Test
    void sharedDocumentsRequireExplicitPublication() {
        stubEmbedding();
        manager.addSharedDocuments(List.of("shared content"), 12L);
        var captor = ArgumentCaptor.forClass(TextSegment.class);
        verify(embeddingStore).add(any(Embedding.class), captor.capture());
        var metadata = captor.getValue().metadata();
        assertEquals(12L, metadata.getLong("knowledge_id"));
        assertEquals("PUBLIC", metadata.getString("visibility"));
        assertFalse(metadata.containsKey("user_id"));
    }

    @Test
    void missingOwnerCannotPublishPrivateDocuments() {
        assertThrows(IllegalArgumentException.class,
                () -> manager.addDocuments(List.of("secret"), 11L, null));
        assertThrows(IllegalArgumentException.class,
                () -> manager.addDocuments(List.of("secret"), 11L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> manager.addDocuments(List.of("secret"), null, 7L));
        assertThrows(IllegalArgumentException.class,
                () -> manager.addSharedDocuments(List.of("secret"), null));
        verifyNoInteractions(embeddingStore, embeddingModel);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bothSearchMethodsFilterBeforeTopK(boolean withScore) {
        stubSearch();
        if (withScore) {
            manager.searchWithScore("question", 7L, 5, 0.5);
        } else {
            manager.search("question", 7L, 5);
        }
        var request = capturedRequest();
        assertEquals(5, request.maxResults());
        var filter = request.filter();
        assertNotNull(filter);
        assertTrue(filter.test(new Metadata().put("user_id", 7L).put("visibility", "PRIVATE")));
        assertFalse(filter.test(new Metadata().put("user_id", 8L).put("visibility", "PRIVATE")));
        assertTrue(filter.test(new Metadata().put("visibility", "PUBLIC")));
        // Unknown and partially migrated metadata must fail closed.
        assertFalse(filter.test(new Metadata().put("knowledge_id", 11L)));
        assertFalse(filter.test(new Metadata().put("user_id", 7L)));
        assertFalse(filter.test(new Metadata().put("visibility", "PRIVATE")));
    }

    @Test
    void nullUserIdCanSearchSharedDocumentsOnly() {
        stubSearch();
        manager.searchWithScore("question", null, 5, 0.5);
        var filter = capturedRequest().filter();
        assertTrue(filter.test(new Metadata().put("visibility", "PUBLIC")));
        assertFalse(filter.test(new Metadata().put("user_id", 7L).put("visibility", "PRIVATE")));
        assertFalse(filter.test(new Metadata()));
    }

    @Test
    void deletionIsRestrictedToOneKnowledgeBase() {
        manager.deleteByKnowledgeId(11L);
        var captor = ArgumentCaptor.forClass(dev.langchain4j.store.embedding.filter.Filter.class);
        verify(embeddingStore).removeAll(captor.capture());
        assertTrue(captor.getValue().test(new Metadata().put("knowledge_id", 11L)));
        assertFalse(captor.getValue().test(new Metadata().put("knowledge_id", 12L)));
        assertThrows(IllegalArgumentException.class, () -> manager.deleteByKnowledgeId(null));
        verify(embeddingStore, never()).removeAll();
    }

    @Test
    void failedEmbeddingDuringRebuildDoesNotDeleteOldVectors() {
        when(embeddingModel.embed("first"))
                .thenReturn(Response.from(Embedding.from(new float[]{1.0f, 0.0f})));
        when(embeddingModel.embed("second")).thenThrow(new IllegalStateException("model unavailable"));
        assertThrows(IllegalStateException.class,
                () -> manager.replaceDocuments(List.of("first", "second"), 11L, 7L));
        verifyNoInteractions(embeddingStore);
    }

    @Test
    void repeatedRebuildsDoNotDuplicateOrExposePrivateDocuments() {
        stubEmbedding();
        var store = new InMemoryEmbeddingStore<TextSegment>();
        var scopedManager = new VectorStoreManagerImpl(store, embeddingModel);
        scopedManager.addDocuments(List.of("old content"), 11L, 7L);
        scopedManager.replaceDocuments(List.of("new content"), 11L, 7L);
        scopedManager.replaceDocuments(List.of("new content"), 11L, 7L);
        assertEquals(List.of("new content"), scopedManager.search("question", 7L, 10));
        assertTrue(scopedManager.search("question", 8L, 10).isEmpty());
    }

    @Test
    void identicalVectorsRemainIsolatedAndDeletedDocumentsDisappear() {
        stubEmbedding();
        var store = new InMemoryEmbeddingStore<TextSegment>();
        var scopedManager = new VectorStoreManagerImpl(store, embeddingModel);
        scopedManager.addDocuments(List.of("A secret"), 11L, 7L);
        scopedManager.addDocuments(List.of("B secret"), 12L, 8L);
        scopedManager.addSharedDocuments(List.of("public"), 13L);
        store.add(Embedding.from(new float[]{1.0f, 0.0f}), TextSegment.from("legacy secret"));

        assertEquals(java.util.Set.of("A secret", "public"),
                new java.util.HashSet<>(scopedManager.search("question", 7L, 10)));
        assertEquals(java.util.Set.of("B secret", "public"),
                new java.util.HashSet<>(scopedManager.search("question", 8L, 10)));
        assertEquals(List.of("public"), scopedManager.search("question", null, 10));
        scopedManager.deleteByKnowledgeId(11L);
        assertEquals(List.of("public"), scopedManager.search("question", 7L, 10));
        assertEquals(java.util.Set.of("B secret", "public"),
                new java.util.HashSet<>(scopedManager.search("question", 8L, 10)));
    }
}
