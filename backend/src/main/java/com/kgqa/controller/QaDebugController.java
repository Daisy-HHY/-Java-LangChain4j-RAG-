package com.kgqa.controller;

import com.kgqa.service.sparql.QueryExecutor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/qa/test")
@Profile("dev")
public class QaDebugController {
    private final QueryExecutor queryExecutor;

    public QaDebugController(QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    @GetMapping("/triples")
    public Map<String, Object> getAllTriples() {
        String sparql = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100";
        List<String> results = queryExecutor.execute(sparql);
        return Map.of(
                "count", results.size(),
                "results", results,
                "tdbPath", queryExecutor.getTdbPath()
        );
    }

    @PostMapping("/sparql")
    public Map<String, Object> executeSparql(@RequestBody Map<String, String> request) {
        String sparql = request.get("sparql");
        if (sparql == null || sparql.isBlank()) {
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
