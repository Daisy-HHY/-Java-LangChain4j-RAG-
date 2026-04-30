package com.kgqa.controller;

import com.kgqa.config.TokenAuthInterceptor;
import com.kgqa.model.dto.ChatRequest;
import com.kgqa.model.dto.ChatResponse;
import com.kgqa.model.dto.SourceItem;
import com.kgqa.model.entity.ChatSession;
import com.kgqa.service.qa.HybridQAService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/qa")
public class QAController {

    private final HybridQAService hybridQAService;
    private final ObjectMapper objectMapper;

    public QAController(HybridQAService hybridQAService, ObjectMapper objectMapper) {
        this.hybridQAService = hybridQAService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
        return hybridQAService.chat(request, currentUserId(servletRequest));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
        SseEmitter emitter = new SseEmitter(180_000L);
        Long userId = currentUserId(servletRequest);

        CompletableFuture.runAsync(() -> {
            try {
                hybridQAService.streamChat(request, userId, new HybridQAService.StreamHandler() {
                    @Override
                    public void onSession(String sessionId) {
                        sendEvent(emitter, "session", new SessionEvent(sessionId));
                    }

                    @Override
                    public void onToken(String token) {
                        sendEvent(emitter, "token", new TokenEvent(token));
                    }

                    @Override
                    public void onSources(List<SourceItem> sources) {
                        sendEvent(emitter, "sources", new SourcesEvent(sources));
                    }

                    @Override
                    public void onComplete(String answer, List<SourceItem> sources, String sessionId) {
                        sendEvent(emitter, "done", new DoneEvent(answer, sources, sessionId));
                        emitter.complete();
                    }
                });
            } catch (Exception e) {
                sendEvent(emitter, "error", new ErrorEvent("回答生成失败，请稍后重试。"));
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/sessions")
    public List<ChatSession> getSessions(HttpServletRequest request) {
        return hybridQAService.getSessions(currentUserId(request));
    }

    @DeleteMapping("/sessions/{id}")
    public boolean deleteSession(@PathVariable("id") String sessionId, HttpServletRequest request) {
        return hybridQAService.deleteSession(sessionId, currentUserId(request));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(TokenAuthInterceptor.USER_ID_ATTRIBUTE);
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("发送流式事件失败", e);
        }
    }

    private record SessionEvent(String sessionId) {}

    private record TokenEvent(String content) {}

    private record SourcesEvent(List<SourceItem> sources) {}

    private record DoneEvent(String answer, List<SourceItem> sources, String sessionId) {}

    private record ErrorEvent(String message) {}
}
