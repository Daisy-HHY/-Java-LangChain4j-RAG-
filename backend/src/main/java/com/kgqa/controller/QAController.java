package com.kgqa.controller;

import com.kgqa.model.dto.ChatRequest;
import com.kgqa.model.dto.ChatResponse;
import com.kgqa.model.entity.ChatSession;
import com.kgqa.service.QAService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/qa")
@CrossOrigin(origins = "*")
public class QAController {

    private final QAService qaService;

    public QAController(QAService qaService) {
        this.qaService = qaService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return qaService.chat(request);
    }

    @GetMapping("/sessions")
    public List<ChatSession> getSessions() {
        return qaService.getSessions();
    }

    @DeleteMapping("/sessions/{id}")
    public boolean deleteSession(@PathVariable("id") String sessionId) {
        return qaService.deleteSession(sessionId);
    }
}
