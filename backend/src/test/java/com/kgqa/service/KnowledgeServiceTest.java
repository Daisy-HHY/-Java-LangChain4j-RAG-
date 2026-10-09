package com.kgqa.service;

import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.repository.KnowledgeChunkRepository;
import com.kgqa.repository.KnowledgeRepository;
import com.kgqa.service.rag.VectorStoreManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeServiceTest {
    @Test
    void uploadWithoutOwnerIsRejectedBeforePersistence() {
        var knowledge = mock(KnowledgeRepository.class);
        var chunks = mock(KnowledgeChunkRepository.class);
        var vectors = mock(VectorStoreManager.class);
        var processing = mock(KnowledgeProcessingService.class);
        var service = new KnowledgeService(knowledge, chunks, vectors, processing);
        var file = new MockMultipartFile("file", "test.txt", "text/plain", "secret".getBytes());
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadAndProcess(file, "title", "", null));
        verifyNoInteractions(knowledge, chunks, vectors, processing);
    }

    @Test
    void uploadPassesAuthenticatedOwnerToAsyncProcessing() throws Exception {
        var knowledge = mock(KnowledgeRepository.class);
        var chunks = mock(KnowledgeChunkRepository.class);
        var vectors = mock(VectorStoreManager.class);
        var processing = mock(KnowledgeProcessingService.class);
        var service = new KnowledgeService(knowledge, chunks, vectors, processing);
        var bytes = "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "test.txt", "text/plain", bytes);
        when(knowledge.insert(any(KnowledgeBase.class))).thenAnswer(invocation -> {
            invocation.<KnowledgeBase>getArgument(0).setId(11L);
            return 1;
        });

        var result = service.uploadAndProcess(file, "title", "", 7L);

        assertEquals(7L, result.getUserId());
        verify(processing).processDocumentAsync(eq(11L), eq(7L), eq(bytes), eq("text/plain"));
    }
}
