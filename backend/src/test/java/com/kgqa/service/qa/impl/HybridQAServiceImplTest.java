package com.kgqa.service.qa.impl;

import com.kgqa.model.dto.ChatRequest;
import com.kgqa.model.entity.ChatSession;
import com.kgqa.repository.ChatMessageMapper;
import com.kgqa.repository.ChatSessionRepository;
import com.kgqa.service.qa.HybridQAService;
import com.kgqa.service.qa.IntentDetectionService;
import com.kgqa.service.qa.MedicalEntityExtractor;
import com.kgqa.service.rag.ChatMemoryService;
import com.kgqa.service.rag.RAGPipeline;
import com.kgqa.service.sparql.QueryExecutor;
import com.kgqa.service.sparql.SPARQLGenerator;
import com.kgqa.service.sparql.SPARQLTemplateMatcher;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HybridQAServiceImplTest {
    @Test
    void regularChatPropagatesUserToRagFallback() {
        var rag = mock(RAGPipeline.class);
        var sessions = mock(ChatSessionRepository.class);
        var memory = mock(ChatMemoryService.class);
        var entities = mock(MedicalEntityExtractor.class);
        var service = service(rag, sessions, memory, entities);
        prepareSession(sessions, memory, entities);
        when(rag.answer("question", List.of(), 7L))
                .thenReturn(new RAGPipeline.Result("answer", List.of()));

        service.chat(request(), 7L);

        verify(rag).answer("question", List.of(), 7L);
    }

    @Test
    void streamingChatPropagatesUserToRagFallback() {
        var rag = mock(RAGPipeline.class);
        var sessions = mock(ChatSessionRepository.class);
        var memory = mock(ChatMemoryService.class);
        var entities = mock(MedicalEntityExtractor.class);
        var service = service(rag, sessions, memory, entities);
        prepareSession(sessions, memory, entities);
        when(rag.answerStreaming(eq("question"), eq(List.of()), eq(7L), any()))
                .thenReturn(new RAGPipeline.Result("answer", List.of()));
        var handler = mock(HybridQAService.StreamHandler.class);

        service.streamChat(request(), 7L, handler);

        verify(rag).answerStreaming(eq("question"), eq(List.of()), eq(7L), any());
        verify(handler).onComplete("answer", List.of(), "session");
    }

    private HybridQAServiceImpl service(RAGPipeline rag, ChatSessionRepository sessions,
                                       ChatMemoryService memory, MedicalEntityExtractor entities) {
        return new HybridQAServiceImpl(rag, sessions, mock(ChatMessageMapper.class), memory,
                mock(StreamingChatModel.class), mock(IntentDetectionService.class), entities,
                mock(SPARQLTemplateMatcher.class), mock(SPARQLGenerator.class), mock(QueryExecutor.class));
    }

    private void prepareSession(ChatSessionRepository sessions, ChatMemoryService memory,
                                MedicalEntityExtractor entities) {
        var session = new ChatSession();
        session.setId(11L);
        session.setSessionId("session");
        session.setUserId(7L);
        session.setTitle("existing");
        when(sessions.selectOne(any())).thenReturn(session);
        when(sessions.selectById(11L)).thenReturn(session);
        when(memory.getChatHistory(11L)).thenReturn(List.of());
        when(entities.extract("question"))
                .thenReturn(new MedicalEntityExtractor.ExtractionResult(List.of(), "unknown"));
    }

    private ChatRequest request() {
        var request = new ChatRequest();
        request.setSessionId("session");
        request.setQuestion("question");
        return request;
    }
}
