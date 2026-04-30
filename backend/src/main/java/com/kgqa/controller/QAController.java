package com.kgqa.controller;

import com.kgqa.config.TokenAuthInterceptor;
import com.kgqa.model.dto.ChatRequest;
import com.kgqa.model.dto.ChatResponse;
import com.kgqa.model.entity.ChatSession;
import com.kgqa.service.qa.HybridQAService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/qa")
public class QAController {

    private final HybridQAService hybridQAService;

    public QAController(HybridQAService hybridQAService) {
        this.hybridQAService = hybridQAService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
        return hybridQAService.chat(request, currentUserId(servletRequest));
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
}
