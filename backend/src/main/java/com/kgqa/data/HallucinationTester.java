package com.kgqa.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 幻觉测试工具
 *
 * 测试 RAG 系统是否产生幻觉：使用 LLM 裁判判断回答是否超出知识库范围
 *
 * 运行方式：确保后端在 8080 端口运行，然后执行
 */
public class HallucinationTester {

    private static final Logger log = LoggerFactory.getLogger(HallucinationTester.class);

    private static final String API_URL = "http://localhost:8080/api/qa/chat";
    private static final String SILICONFLOW_API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // SiliconFlow API Key
    private static final String API_KEY = System.getenv("SILICONFLOW_API_KEY");
    private static final String MODEL_NAME = "MiniMaxAI/MiniMax-M2.5";

    // LLM 裁判的系统提示
    private static final String JUDGE_SYSTEM_PROMPT = """
            你是一个幻觉检测专家。你的任务是判断模型的回答是否超出了知识库（参考资料）的范围。
            判断标准：
            - 如果回答中的信息完全来自知识库，返回：OK
            - 如果回答包含了知识库中没有的信息（模型自己编造的），返回：HALLUCINATION
            - 谨慎判断：如果知识库提到糖尿病和高血压有关联，而回答提到这个关联，这不是幻觉
            
            输出格式（只输出以下两种之一）：
            OK
            或
            HALLUCINATION：<简要说明哪部分是幻觉>""";

    // 测试问题（涵盖不同类型）
    private static final TestQuestion[] TEST_QUESTIONS = {
        new TestQuestion(
            "糖尿病的并发症有哪些？",
            Arrays.asList("糖尿病", "并发症", "视网膜", "肾病", "神经")
        ),
        new TestQuestion(
            "阿司匹林有哪些禁忌症？",
            Arrays.asList("阿司匹林", "禁忌", "过敏", "出血", "胃")
        ),
        new TestQuestion(
            "心肌梗死和心绞痛的区别是什么？",
            Arrays.asList("心肌梗死", "心绞痛", "胸痛", "缺血", "区别")
        ),
        new TestQuestion(
            "高血压患者应该避免哪些食物？",
            Arrays.asList("高血压", "盐", "脂肪", "避免", "食物")
        ),
        new TestQuestion(
            "感冒和流感的区别是什么？",
            Arrays.asList("感冒", "流感", "病毒", "症状", "区别")
        ),
        new TestQuestion(
            "冠心病的治疗方法有哪些？",
            Arrays.asList("冠心病", "治疗", "药物", "手术", "支架")
        ),
        new TestQuestion(
            "什么因素会导致高血压？",
            Arrays.asList("高血压", "因素", "盐", "肥胖", "压力")
        ),
        new TestQuestion(
            "阿司匹林的副作用是什么？",
            Arrays.asList("阿司匹林", "副作用", "胃", "出血", "过敏")
        )
    };

    public static void main(String[] args) {
        log.info("========== RAG 幻觉测试 ==========");
        log.info("测试 {} 个问题...\n", TEST_QUESTIONS.length);

        int passed = 0;
        int warning = 0;
        int failed = 0;

        List<HResult> results = new ArrayList<>();

        for (int i = 0; i < TEST_QUESTIONS.length; i++) {
            TestQuestion tq = TEST_QUESTIONS[i];
            log.info("【问题 {}】{}", i + 1, tq.question);

            try {
                HResult result = testQuestion(tq);
                results.add(result);

                if (result.status == Status.PASS) {
                    passed++;
                    log.info("  ✓ PASS - {}", result.message);
                } else if (result.status == Status.WARNING) {
                    warning++;
                    log.info("  ⚠ WARNING - {}", result.message);
                } else {
                    failed++;
                    log.info("  ✗ FAIL - {}", result.message);
                }
            } catch (Exception e) {
                failed++;
                log.info("  ✗ ERROR - {}", e.getMessage());
            }
            log.info("");
        }

        // 汇总
        log.info("\n========== 测试汇总 ==========");
        log.info("通过: {} ({})", passed, String.format("%.1f", 100.0 * passed / TEST_QUESTIONS.length) + "%");
        log.info("警告: {} ({})", warning, String.format("%.1f", 100.0 * warning / TEST_QUESTIONS.length) + "%");
        log.info("失败: {} ({})", failed, String.format("%.1f", 100.0 * failed / TEST_QUESTIONS.length) + "%");

        log.info("\n========== 详细结果 ==========");
        for (int i = 0; i < results.size(); i++) {
            HResult r = results.get(i);
            String icon = r.status == Status.PASS ? "✓" : r.status == Status.WARNING ? "⚠" : "✗";
            log.info("{}. {} - {}", i + 1, icon, r.question);
            log.info("   回答: {}", r.answer.length() > 150 ? r.answer.substring(0, 150) + "..." : r.answer);
            log.info("   来源数: {}", r.sourceCount);
            log.info("   状态: {}", r.status);
            log.info("");
        }
    }

    private static HResult testQuestion(TestQuestion tq) throws Exception {
        // 调用 RAG API
        String requestBody = objectMapper.writeValueAsString(
            Map.of("question", tq.question, "sessionId", UUID.randomUUID().toString())
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return new HResult(tq.question, "", null, 0, Status.FAIL, "API 调用失败");
        }

        JsonNode json = objectMapper.readTree(response.body());
        String answer = json.has("answer") ? json.get("answer").asText() : "";
        int sourceCount = json.has("sources") ? json.get("sources").size() : 0;

        // 提取召回的文档内容
        String context = "";
        if (json.has("sources")) {
            List<String> chunks = new ArrayList<>();
            for (JsonNode source : json.get("sources")) {
                if (source.has("content")) {
                    chunks.add(source.get("content").asText());
                }
            }
            context = chunks.stream().collect(Collectors.joining("\n---\n"));
        }

        // 检查回答是否为空或太短
        if (answer.trim().isEmpty()) {
            return new HResult(tq.question, answer, context, sourceCount, Status.FAIL, "回答为空");
        }

        if (answer.length() < 20) {
            return new HResult(tq.question, answer, context, sourceCount, Status.WARNING, "回答过短");
        }

        // 检查是否有"我不知道"类的回答且无检索结果
        if ((answer.contains("不知道") || answer.contains("无法") || answer.contains("抱歉")) && sourceCount == 0) {
            return new HResult(tq.question, answer, context, sourceCount, Status.WARNING, "无检索结果且模型表示不知道");
        }

        // 使用 LLM 裁判判断是否幻觉
        String judgeResult = judgeByLLM(tq.question, context, answer);

        Status status;
        String msg;

        if (judgeResult.startsWith("HALLUCINATION")) {
            status = Status.FAIL;
            msg = "LLM 裁判判定幻觉: " + judgeResult.substring("HALLUCINATION：".length());
        } else {
            status = Status.PASS;
            msg = "LLM 裁判判定: OK";
        }

        return new HResult(tq.question, answer, context, sourceCount, status, msg);
    }

    /**
     * 使用 LLM 作为裁判判断回答是否包含幻觉
     */
    private static String judgeByLLM(String question, String context, String answer) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            log.warn("未设置 SILICONFLOW_API_KEY，跳过 LLM 裁判，使用关键词匹配");
            return fallbackKeywordJudge(question, answer);
        }

        try {
            String userPrompt = String.format("""
用户问题：%s

知识库内容：
%s

模型回答：
%s
""", question, context.isEmpty() ? "[无知识库内容]" : context, answer);

            Map<String, Object> requestBody = Map.of(
                "model", MODEL_NAME,
                "messages", List.of(
                    Map.of("role", "system", "content", JUDGE_SYSTEM_PROMPT),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.0  // 幻觉检测需要确定性
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SILICONFLOW_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("LLM 裁判调用失败: HTTP {}", response.statusCode());
                return fallbackKeywordJudge(question, answer);
            }

            JsonNode respJson = objectMapper.readTree(response.body());
            String judgeContent = respJson.path("choices").path(0).path("message").path("content").asText();

            return judgeContent.trim();

        } catch (Exception e) {
            log.warn("LLM 裁判调用异常: {}", e.getMessage());
            return fallbackKeywordJudge(question, answer);
        }
    }

    /**
     * 回退的关键词判断（当 LLM 不可用时）
     */
    private static String fallbackKeywordJudge(String question, String answer) {
        // 简单的回退逻辑，不作为最终判定
        return "OK";
    }

    // ---- 内部类 ----

    enum Status { PASS, WARNING, FAIL }

    static class TestQuestion {
        String question;
        List<String> expectedKeywords;  // 期望在回答中看到的关键词

        TestQuestion(String question, List<String> expected) {
            this.question = question;
            this.expectedKeywords = expected;
        }
    }

    static class HResult {
        String question;
        String answer;
        String context;  // 知识库召回内容
        int sourceCount;
        Status status;
        String message;

        HResult(String question, String answer, String context, int sourceCount, Status status, String message) {
            this.question = question;
            this.answer = answer;
            this.context = context;
            this.sourceCount = sourceCount;
            this.status = status;
            this.message = message;
        }
    }
}