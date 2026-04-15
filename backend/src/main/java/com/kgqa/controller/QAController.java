package com.kgqa.controller;

import com.kgqa.model.dto.ChatRequest;
import com.kgqa.model.dto.ChatResponse;
import com.kgqa.model.entity.ChatSession;
import com.kgqa.service.qa.HybridQAService;
import com.kgqa.service.sparql.QueryExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/qa")
@CrossOrigin(origins = "*")
public class QAController {

    private final HybridQAService hybridQAService;
    private final QueryExecutor queryExecutor;

    public QAController(HybridQAService hybridQAService, QueryExecutor queryExecutor) {
        this.hybridQAService = hybridQAService;
        this.queryExecutor = queryExecutor;
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

    /**
     * 测试接口：查询 TDB 中所有三元组
     */
    @GetMapping("/test/triples")
    public Map<String, Object> getAllTriples() {
        String sparql = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100";
        List<String> results = queryExecutor.execute(sparql);
        return Map.of(
                "count", results.size(),
                "results", results,
                "tdbPath", queryExecutor.getTdbPath()
        );
    }

    /**
     * 测试接口：执行自定义 SPARQL 查询
     */
    @PostMapping("/test/sparql")
    public Map<String, Object> executeSparql(@RequestBody Map<String, String> request) {
        String sparql = request.get("sparql");
        if (sparql == null || sparql.isEmpty()) {
            return Map.of("error", "sparql is required");
        }
        List<String> results = queryExecutor.execute(sparql);
        return Map.of(
                "sparql", sparql,
                "count", results.size(),
                "results", results
        );
    }
}
