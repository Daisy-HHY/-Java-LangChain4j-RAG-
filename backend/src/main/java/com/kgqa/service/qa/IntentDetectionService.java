package com.kgqa.service.qa;

/**
 * 意图识别服务接口
 * 使用 LLM 对用户问题进行分类
 */
public interface IntentDetectionService {

    /**
     * 问题类型枚举
     */
    enum QuestionType {
        FACT_QUERY,      // 事实查询
        COMPARISON,      // 比较问题
        EXPLANATION,     // 解释问题
        OPEN_QUESTION,   // 开放问题
        AGGREGATION      // 聚合统计
    }

    /**
     * 识别问题意图
     * @param question 用户问题
     * @return 问题类型
     */
    QuestionType detect(String question);
}
