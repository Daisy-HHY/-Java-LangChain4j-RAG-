package com.kgqa.controller;

import com.kgqa.config.TokenAuthInterceptor;
import com.kgqa.model.dto.KnowledgeDTO;
import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.service.KnowledgeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "tags", required = false) String tags,
            HttpServletRequest request) {
        try {
            KnowledgeBase knowledge = knowledgeService.uploadAndProcess(file, title, tags, currentUserId(request));
            Map<String, Object> result = new HashMap<>();
            result.put("knowledgeId", knowledge.getId());
            result.put("status", knowledge.getStatus());
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "FAILED");
            result.put("message", e.getMessage());
            return result;
        }
    }

    @GetMapping("/list")
    public List<KnowledgeDTO> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            HttpServletRequest request) {
        return knowledgeService.list(page, size, currentUserId(request));
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") Long id, HttpServletRequest request) {
        return knowledgeService.delete(id, currentUserId(request));
    }

    @GetMapping("/{id}/status")
    public Map<String, Object> getStatus(@PathVariable("id") Long id, HttpServletRequest request) {
        String status = knowledgeService.getStatus(id, currentUserId(request));
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        return result;
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(TokenAuthInterceptor.USER_ID_ATTRIBUTE);
    }
}
