package com.kgqa.service.qa.impl;

import com.kgqa.service.qa.IntentDetectionService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务实现
 * 使用 LLM 对用户问题进行分类
 */
@Service
public class IntentDetectionServiceImpl implements IntentDetectionService {

    private final ChatModel chatModel;

    // 中文提示词
    private static final String INTENT_PROMPT = """
            你是一个医学问答系统的意图识别助手。请分析用户问题的意图类型。

            问题类型定义：
            1. FACT_QUERY（事实查询）：询问某个实体（如药物、疾病）的特定属性或关系
              - 例："阿司匹林的适应症是什么？"
              - 例："高血压有什么症状？"
              - 例："哪些药物可以治疗糖尿病？"

            2. COMPARISON（比较问题）：比较两个或多个实体的异同
              - 例："阿司匹林和布洛芬有什么区别？"
              - 例："心肌梗死和心绞痛有什么不同？"

            3. EXPLANATION（解释问题）：解释某个概念、疾病或机制
              - 例："解释一下心肌梗死的发病机制"
              - 例："什么是糖尿病？"
              - 例："介绍一下癌症的转移过程"

            4. OPEN_QUESTION（开放问题）：没有明确答案范围的自由问答
              - 例："感冒了怎么办？"
              - 例："如何保持健康？"
              - 例："最近有什么新的治疗方法？"

            5. AGGREGATION（聚合统计）：需要统计或计数的查询
              - 例："有多少种药物可以治疗高血压？"
              - 例："糖尿病的并发症有哪些？"

            请分析以下问题，只返回类型名称（FACT_QUERY、COMPARISON、EXPLANATION、OPEN_QUESTION 或 AGGREGATION），不要返回其他内容。

            问题：%s
            """;

    public IntentDetectionServiceImpl(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 识别问题意图
     */
    @Override
    public QuestionType detect(String question) {
        if (question == null || question.trim().isEmpty()) {
            return QuestionType.OPEN_QUESTION;
        }

        try {
            String prompt = String.format(INTENT_PROMPT, question);
            String response = chatModel.chat(prompt).trim().toUpperCase();

            // 解析返回的类型
            if (response.contains("FACT_QUERY")) {
                return QuestionType.FACT_QUERY;
            } else if (response.contains("COMPARISON")) {
                return QuestionType.COMPARISON;
            } else if (response.contains("EXPLANATION")) {
                return QuestionType.EXPLANATION;
            } else if (response.contains("AGGREGATION")) {
                return QuestionType.AGGREGATION;
            } else {
                return QuestionType.OPEN_QUESTION;
            }
        } catch (Exception e) {
            // 如果 LLM 调用失败，使用关键词进行简单判断
            return detectByKeywords(question);
        }
    }

    /**
     * 基于关键词的简单意图识别（备用方案）
     */
    private QuestionType detectByKeywords(String question) {
        String q = question.toLowerCase();

        // 事实查询关键词
        if (q.contains("是什么") || q.contains("有什么") || q.contains("哪些") ||
                q.contains("适应症") || q.contains("副作用") || q.contains("禁忌") ||
                q.contains("用法") || q.contains("剂量")) {
            return QuestionType.FACT_QUERY;
        }

        // 比较问题关键词
        if (q.contains("区别") || q.contains("不同") || q.contains("比较") ||
                q.contains("差异") || q.contains("vs") || q.contains(" versus ")) {
            return QuestionType.COMPARISON;
        }

        // 解释问题关键词
        if (q.contains("解释") || q.contains("介绍") || q.contains("什么是") ||
                q.contains("如何") || q.contains("机制") || q.contains("原理")) {
            return QuestionType.EXPLANATION;
        }

        // 聚合统计关键词
        if (q.contains("多少") || q.contains("有哪些") || q.contains("列举") ||
                q.contains("所有") || q.contains("列表")) {
            return QuestionType.AGGREGATION;
        }

        return QuestionType.OPEN_QUESTION;
    }
}
