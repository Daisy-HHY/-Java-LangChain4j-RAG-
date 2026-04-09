package com.kgqa.controller;

import com.kgqa.model.dto.ChatRequest;
import com.kgqa.model.dto.ChatResponse;
import com.kgqa.model.entity.ChatSession;
import com.kgqa.service.qa.HybridQAService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/qa")
@CrossOrigin(origins = "*")
public class QAController {

    private final HybridQAService hybridQAService;

    public QAController(HybridQAService hybridQAService) {
        this.hybridQAService = hybridQAService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return hybridQAService.chat(request);
    }

    @GetMapping("/sessions")
    public List<ChatSession> getSessions() {
        return hybridQAService.getSessions();
    }

    @DeleteMapping("/sessions/{id}")
    public boolean deleteSession(@PathVariable("id") String sessionId) {
        return hybridQAService.deleteSession(sessionId);
    }
}
