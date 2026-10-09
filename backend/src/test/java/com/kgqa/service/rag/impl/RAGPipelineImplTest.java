package com.kgqa.service.rag.impl;

import com.kgqa.service.rag.VectorStoreManager;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RAGPipelineImplTest {
    @Test
    void regularFallbackKeepsUserScopeAtBothThresholds() {
        var vectors = mock(VectorStoreManager.class);
        var chat = mock(ChatModel.class);
        var streamingChat = mock(StreamingChatModel.class);
        when(vectors.searchWithScore("question", 7L, 5, 0.75)).thenReturn(List.of());
        when(vectors.searchWithScore("question", 7L, 5, 0.5)).thenReturn(List.of());
        var pipeline = new RAGPipelineImpl(vectors, chat, streamingChat);

        var result = pipeline.answer("question", List.of(), 7L);

        assertTrue(result.sources().isEmpty());
        verify(vectors).searchWithScore("question", 7L, 5, 0.75);
        verify(vectors).searchWithScore("question", 7L, 5, 0.5);
        verifyNoMoreInteractions(vectors);
        verifyNoInteractions(chat, streamingChat);
    }

    @Test
    void streamingFallbackKeepsUserScopeAtBothThresholds() {
        var vectors = mock(VectorStoreManager.class);
        var chat = mock(ChatModel.class);
        var streamingChat = mock(StreamingChatModel.class);
        when(vectors.searchWithScore("question", 8L, 5, 0.75)).thenReturn(List.of());
        when(vectors.searchWithScore("question", 8L, 5, 0.5)).thenReturn(List.of());
        var pipeline = new RAGPipelineImpl(vectors, chat, streamingChat);
        var tokens = new ArrayList<String>();

        var result = pipeline.answerStreaming("question", List.of(), 8L, tokens::add);

        assertTrue(result.sources().isEmpty());
        assertEquals(List.of(result.answer()), tokens);
        verify(vectors).searchWithScore("question", 8L, 5, 0.75);
        verify(vectors).searchWithScore("question", 8L, 5, 0.5);
        verifyNoMoreInteractions(vectors);
        verifyNoInteractions(chat, streamingChat);
    }
}
