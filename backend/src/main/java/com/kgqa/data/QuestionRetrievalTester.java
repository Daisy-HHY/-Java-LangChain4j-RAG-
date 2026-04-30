package com.kgqa.data;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 真实问题检索测试工具
 */
public class QuestionRetrievalTester {

    private static final Logger log = LoggerFactory.getLogger(QuestionRetrievalTester.class);

    private static final String[] TEST_QUESTIONS = {
        "糖尿病的并发症有哪些？",
        "高血压的早期症状是什么？",
        "阿司匹林有哪些禁忌症？",
        "心肌梗死和心绞痛的区别是什么？",
        "如何预防感冒？",
        "糖尿病患者应该避免哪些食物？",
        "阿司匹林的副作用是什么？",
        "冠心病的治疗方法有哪些？",
        "什么因素会导致高血压？",
        "感冒和流感的区别是什么？"
    };

    private static final int TOP_K = 5;
    private static final double[] THRESHOLDS = {0.3, 0.4, 0.5, 0.6, 0.7, 0.75, 0.8, 0.85, 0.9};

    private static final String JDBC_URL = System.getenv().getOrDefault("KGQA_DB_URL", "jdbc:postgresql://localhost:5432/kgqa");
    private static final String USERNAME = System.getenv().getOrDefault("KGQA_DB_USERNAME", "postgres");
    private static final String PASSWORD = System.getenv().getOrDefault("KGQA_DB_PASSWORD", "123456");
    private static final String TABLE_NAME = System.getenv().getOrDefault("KGQA_EMBEDDING_TABLE", "embeddings");

    public static void main(String[] args) {
        log.info("========== 真实问题检索测试 ==========");

        try {
            EmbeddingModel embeddingModel = createEmbeddingModel();
            EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(embeddingModel);

            log.info("开始测试 {} 个问题...\n", TEST_QUESTIONS.length);

            Map<String, List<RetrievalResult>> allResults = new LinkedHashMap<>();

            for (int i = 0; i < TEST_QUESTIONS.length; i++) {
                String question = TEST_QUESTIONS[i];
                log.info("【问题 {}】{}", i + 1, question);

                var queryEmbedding = embeddingModel.embed(question).content();

                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(TOP_K)
                        .build();
                var matches = embeddingStore.search(request).matches();

                List<RetrievalResult> results = new ArrayList<>();
                for (int j = 0; j < matches.size(); j++) {
                    var match = matches.get(j);
                    double score = match.score() == null ? 0.0 : match.score();
                    String text = match.embedded().text();
                    String snippet = text.length() > 80 ? text.substring(0, 80) + "..." : text;

                    results.add(new RetrievalResult(score, text));
                    log.info("  [{}] {}", String.format("%.3f", score), snippet);
                }

                allResults.put(question, results);
                log.info("");
            }

            printScoreDistribution(allResults);

        } catch (Exception e) {
            log.error("测试失败", e);
        }
    }

    private static EmbeddingModel createEmbeddingModel() {
        String apiKey = System.getenv("SILICONFLOW_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请设置 SILICONFLOW_API_KEY 环境变量");
        }
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.siliconflow.cn/v1")
                .modelName("Qwen/Qwen3-Embedding-8B")
                .build();
    }

    private static EmbeddingStore<TextSegment> createEmbeddingStore(EmbeddingModel embeddingModel) {
        return PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .database("kgqa")
                .user(USERNAME)
                .password(PASSWORD)
                .table(TABLE_NAME)
                .dimension(embeddingModel.dimension())
                .build();
    }

    private static void printScoreDistribution(Map<String, List<RetrievalResult>> allResults) {
        log.info("\n========== 分数分布统计 ==========");

        List<Double> allScores = new ArrayList<>();
        for (List<RetrievalResult> results : allResults.values()) {
            for (RetrievalResult r : results) {
                allScores.add(r.score);
            }
        }

        Collections.sort(allScores);

        int[] bucketCounts = new int[10];
        for (double s : allScores) {
            int idx = Math.min((int)(s * 10), 9);
            bucketCounts[idx]++;
        }

        log.info("分数区间分布：");
        for (int i = 0; i < 10; i++) {
            double rangeStart = i / 10.0;
            double rangeEnd = (i + 1) / 10.0;
            int count = bucketCounts[i];
            double pct = 100.0 * count / allScores.size();
            log.info("  [{}-{}): {} 条 ({}%)",
                    String.format("%.1f", rangeStart), String.format("%.1f", rangeEnd),
                    count, String.format("%.1f", pct));
        }

        log.info("\n分数百分位数：");
        int[] percentiles = {10, 25, 50, 75, 90};
        for (int p : percentiles) {
            int idx = (int) Math.ceil(p / 100.0 * allScores.size()) - 1;
            idx = Math.max(0, Math.min(idx, allScores.size() - 1));
            log.info("  P{}: {}", p, String.format("%.3f", allScores.get(idx)));
        }

        log.info("\n不同阈值下的覆盖率（至少有一个结果）：");
        for (double thresh : THRESHOLDS) {
            int count = 0;
            for (List<RetrievalResult> results : allResults.values()) {
                boolean hasAbove = results.stream().anyMatch(r -> r.score >= thresh);
                if (hasAbove) count++;
            }
            log.info("  阈值 >= {}: {}/{} ({}%)",
                    String.format("%.2f", thresh), count, allResults.size(),
                    String.format("%.1f", 100.0 * count / allResults.size()));
        }
    }

    static class RetrievalResult {
        final double score;
        final String text;
        RetrievalResult(double score, String text) {
            this.score = score;
            this.text = text;
        }
    }
}
