package com.kgqa.controller;

import com.kgqa.model.dto.KnowledgeDTO;
import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.service.KnowledgeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "tags", required = false) String tags) {
        try {
            KnowledgeBase knowledge = knowledgeService.uploadAndProcess(file, title, tags);
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
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return knowledgeService.list(page, size);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return knowledgeService.delete(id);
    }

    @GetMapping("/{id}/status")
    public Map<String, Object> getStatus(@PathVariable("id") Long id) {
        String status = knowledgeService.getStatus(id);
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        return result;
    }
}
