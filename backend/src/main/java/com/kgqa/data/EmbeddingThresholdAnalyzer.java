package com.kgqa.data;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * 向量检索阈值分析工具
 *
 * 分析策略：Self-Retrieval
 * - 从向量库随机抽样 N 条文本
 * - 用这些文本作为查询，检索 top5
 * - 检查自己是否在 top3 中（预期应该 > 95%）
 * - 统计不同阈值下的召回率和分数分布
 *
 * 运行方式：直接运行 main 方法
 */
public class EmbeddingThresholdAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingThresholdAnalyzer.class);

    // 分析参数
    private static final int SAMPLE_SIZE = 100;        // 抽样数量
    private static final int TOP_K = 5;                // 检索 top5
    private static final int SELF_TOP_K = 3;           // 期望自己在 top3

    // 数据库配置（请根据实际情况修改）
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/kgqa";
    private static final String USERNAME = "postgres";
    private static final String PASSWORD = "123456";
    private static final String TABLE_NAME = "embeddings";

    public static void main(String[] args) {
        log.info("========== 向量检索阈值分析工具 ==========");

        try {
            // 1. 连接 EmbeddingStore（会自动用 SiliconFlow API）
            log.info("连接向量库...");
            EmbeddingModel embeddingModel = createEmbeddingModel();
            EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(embeddingModel);

            // 2. 从数据库随机抽样文本
            log.info("从向量库随机抽样 {} 条...", SAMPLE_SIZE);
            List<SampleDoc> samples = randomSampleFromDb(SAMPLE_SIZE);
            if (samples.isEmpty()) {
                log.error("向量库为空或无法连接！");
                return;
            }
            log.info("成功抽样 {} 条", samples.size());

            // 3. 对每个样本做 self-retrieval
            log.info("开始 Self-Retrieval 分析...");
            List<ScoreBucket> scoreBuckets = new ArrayList<>();
            int selfInTop3 = 0;
            int selfInTop5 = 0;

            for (int i = 0; i < samples.size(); i++) {
                SampleDoc sample = samples.get(i);
                if (i % 20 == 0) {
                    log.info("进度: {}/{}", i, samples.size());
                }

                // 嵌入查询
                var queryEmbedding = embeddingModel.embed(sample.text).content();

                // 检索 top5
                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(TOP_K)
                        .build();
                var results = embeddingStore.search(request).matches();

                // 找到自己的排名
                int selfRank = -1;
                double selfScore = -1;
                for (int j = 0; j < results.size(); j++) {
                    String retrievedText = results.get(j).embedded().text();
                    if (retrievedText.equals(sample.text)) {
                        selfRank = j + 1; // 1-based
                        selfScore = results.get(j).score();
                        break;
                    }
                }

                // 统计
                if (selfRank > 0 && selfRank <= SELF_TOP_K) {
                    selfInTop3++;
                    selfInTop5++;
                    scoreBuckets.add(new ScoreBucket(selfScore, true, selfRank));
                } else if (selfRank > 0 && selfRank <= TOP_K) {
                    selfInTop5++;
                    scoreBuckets.add(new ScoreBucket(selfScore, false, selfRank));
                } else {
                    // 自己没在 top5，说明阈值过高
                    scoreBuckets.add(new ScoreBucket(0.0, false, -1));
                }
            }

            // 4. 输出分析报告
            printReport(scoreBuckets, selfInTop3, selfInTop5);

        } catch (Exception e) {
            log.error("分析失败", e);
        }
    }

        private static EmbeddingModel createEmbeddingModel() {
        // 通过 SiliconFlow API 创建 embedding 模型（与 SiliconFlowConfig 保持一致）
        String apiKey = System.getenv("SILICONFLOW_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("请设置 SILICONFLOW_API_KEY 环境变量");
        }

        return dev.langchain4j.model.openai.OpenAiEmbeddingModel.builder()
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

    /**
     * 从数据库随机抽样
     */
    private static List<SampleDoc> randomSampleFromDb(int limit) throws Exception {
        List<SampleDoc> samples = new ArrayList<>();

        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
             Statement stmt = conn.createStatement()) {

                        // 随机抽样
            String sql = String.format(
                "SELECT text FROM %s ORDER BY RANDOM() LIMIT %d",
                TABLE_NAME, limit
            );

try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    samples.add(new SampleDoc(rs.getString("text")));
                }
            }
        }

        return samples;
    }

    /**
     * 打印分析报告
     */
    private static void printReport(List<ScoreBucket> buckets, int selfInTop3, int selfInTop5) {
        log.info("\n========== 分析报告 ==========");
        log.info("抽样总数: {}", buckets.size());
        log.info("Self-Top3 命中率: {}/{} ({}%)",
                selfInTop3, buckets.size(), String.format("%.1f", 100.0 * selfInTop3 / buckets.size()));
        log.info("Self-Top5 命中率: {}/{} ({}%)",
                selfInTop5, buckets.size(), String.format("%.1f", 100.0 * selfInTop5 / buckets.size()));

        // 分数分布统计
        log.info("\n--- 分数分布 ---");
        int[] bucketCounts = new int[10]; // 0.0-0.1, 0.1-0.2, ..., 0.9-1.0
        double sum = 0, min = Double.MAX_VALUE, max = 0;

        for (ScoreBucket b : buckets) {
            if (b.score > 0) {
                int idx = Math.min((int)(b.score * 10), 9);
                bucketCounts[idx]++;
                sum += b.score;
                min = Math.min(min, b.score);
                max = Math.max(max, b.score);
            }
        }

        for (int i = 0; i < 10; i++) {
            double rangeStart = i / 10.0;
            double rangeEnd = (i + 1) / 10.0;
            int count = bucketCounts[i];
            double pct = 100.0 * count / buckets.size();
            log.info("[{}-{}): {} 条 ({}%)", rangeStart, rangeEnd, count, String.format("%.1f", pct));
        }

        double avg = sum / buckets.size();
        log.info("\n分数统计: min={}, max={}, avg={}", String.format("%.3f", min), String.format("%.3f", max), String.format("%.3f", avg));

        // 推荐阈值
        log.info("\n--- 推荐阈值 ---");
        log.info("保守策略（高 Precision）: 阈值={}，召回约{}%",
                String.format("%.2f", findThresholdForRecall(buckets, 0.95)), 95);
        log.info("平衡策略（Precision/Recall 均衡）: 阈值={}",
                String.format("%.2f", findThresholdForRecall(buckets, 0.80)));
        log.info("激进策略（高 Recall）: 阈值={}，召回约{}%",
                String.format("%.2f", findThresholdForRecall(buckets, 0.50)), 50);
    }

    /**
     * 找到达到目标召回率需要的阈值
     */
    private static double findThresholdForRecall(List<ScoreBucket> buckets, double targetRecall) {
        // 按分数降序排列
        List<Double> scores = new ArrayList<>();
        for (ScoreBucket b : buckets) {
            if (b.foundInTop3) {
                scores.add(b.score);
            }
        }
        Collections.sort(scores, Collections.reverseOrder());

        if (scores.isEmpty()) return 1.0;

        int idx = (int)(scores.size() * (1 - targetRecall));
        idx = Math.max(0, Math.min(idx, scores.size() - 1));
        return Math.round(scores.get(idx) * 100) / 100.0;
    }

    // ---- 内部类 ----

    static class SampleDoc {
        final String text;
        SampleDoc(String text) {
            this.text = text;
        }
    }

    static class ScoreBucket {
        final double score;
        final boolean foundInTop3;
        final int rank;
        ScoreBucket(double score, boolean foundInTop3, int rank) {
            this.score = score;
            this.foundInTop3 = foundInTop3;
            this.rank = rank;
        }
    }
}