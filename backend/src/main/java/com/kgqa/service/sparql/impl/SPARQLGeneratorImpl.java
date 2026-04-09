package com.kgqa.service.sparql.impl;

import com.kgqa.service.qa.MedicalEntityExtractor;
import com.kgqa.service.sparql.SPARQLGenerator;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

/**
 * SPARQL 生成器实现
 * 当模板匹配失败时，使用 LLM 根据问题生成 SPARQL
 */
@Service
public class SPARQLGeneratorImpl implements SPARQLGenerator {

    private final ChatModel chatModel;

    // SPARQL 生成提示词
    private static final String SPARQL_GENERATION_PROMPT = """
            你是一个 SPARQL 查询生成助手。请根据用户问题和已提取的实体，生成 SPARQL 查询语句。

            知识图谱的 RDF 三元组格式：
            - 主体 <关系> 客体
            - 例如：阿司匹林 <适应症> 发热
            - 例如：高血压 <症状> 头痛

            常见的关系类型：
            - 适应症：药物可以治疗的疾病
            - 副作用：药物可能引起的不良反应
            - 禁忌：不宜使用的情况
            - 症状：疾病的表现
            - 治疗：疾病的治疗方法
            - 诊断：疾病的诊断方法
            - 病因：疾病的发病原因
            - 用法：药物的使用方法
            - 剂量：药物的使用剂量

            请生成 SPARQL SELECT 查询语句，只返回查询语句，不要返回其他内容。

            用户问题：%s

            已识别的实体：
            %s

            SPARQL 查询：
            """;

    // 简化版本的 SPARQL 生成（不依赖 LLM，用于简单查询）
    private static final String SIMPLE_GENERATION_PROMPT = """
            请根据以下信息生成 SPARQL 查询：

            问题：%s
            实体类型：%s
            实体值：%s
            查询意图：%s

            请生成只返回 SPARQL 查询语句。
            """;

    public SPARQLGeneratorImpl(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 使用 LLM 生成 SPARQL 查询
     */
    @Override
    public String generate(String question, MedicalEntityExtractor.ExtractionResult entities) {
        if (question == null || question.isEmpty()) {
            return null;
        }

        try {
            // 格式化实体信息
            String entityInfo = formatEntityInfo(entities);

            // 调用 LLM 生成 SPARQL
            String prompt = String.format(SPARQL_GENERATION_PROMPT, question, entityInfo);
            String response = chatModel.chat(prompt).trim();

            // 验证生成的 SPARQL
            if (isValidSPARQL(response)) {
                return response;
            }

            // 如果 LLM 生成的格式不正确，尝试简单生成
            return generateSimple(question, entities);
        } catch (Exception e) {
            // LLM 调用失败时返回 null，让系统 fallback 到 RAG
            return null;
        }
    }

    /**
     * 简单的 SPARQL 生成（基于规则）
     */
    @Override
    public String generateSimple(String question, MedicalEntityExtractor.ExtractionResult entities) {
        if (entities == null || entities.entities().isEmpty()) {
            return null;
        }

        try {
            // 获取第一个实体
            MedicalEntityExtractor.MedicalEntity entity = entities.entities().get(0);
            String entityType = entity.type();
            String entityValue = entity.value();
            String intent = entities.intent();

            String sparql;

            // 根据意图选择谓词
            String predicate = selectPredicate(intent, entityType);

            if (predicate != null) {
                // 构建简单查询
                if (intent.contains("哪些") || intent.contains("列表")) {
                    // 需要返回多个结果
                    sparql = String.format(
                            "SELECT ?answer WHERE { ?answer <%s> <%s> } LIMIT 20",
                            predicate, entityValue
                    );
                } else {
                    // 返回单个结果
                    sparql = String.format(
                            "SELECT ?answer WHERE { <%s> <%s> ?answer } LIMIT 10",
                            entityValue, predicate
                    );
                }
                return sparql;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据意图选择谓词
     */
    private String selectPredicate(String intent, String entityType) {
        if (intent.contains("适应症")) {
            return entityType.equals("药物") ? "适应症" : null;
        }
        if (intent.contains("副作用")) {
            return "副作用";
        }
        if (intent.contains("禁忌")) {
            return "禁忌";
        }
        if (intent.contains("症状")) {
            return "症状";
        }
        if (intent.contains("治疗")) {
            return "治疗";
        }
        if (intent.contains("诊断")) {
            return "诊断";
        }
        if (intent.contains("病因")) {
            return "病因";
        }
        if (intent.contains("用法")) {
            return "用法";
        }
        if (intent.contains("剂量")) {
            return "剂量";
        }

        // 默认谓词
        if (entityType.equals("药物")) {
            return "适应症";
        } else if (entityType.equals("疾病")) {
            return "症状";
        }

        return null;
    }

    /**
     * 格式化实体信息
     */
    private String formatEntityInfo(MedicalEntityExtractor.ExtractionResult entities) {
        if (entities == null || entities.entities().isEmpty()) {
            return "无";
        }

        StringBuilder sb = new StringBuilder();
        for (MedicalEntityExtractor.MedicalEntity entity : entities.entities()) {
            sb.append(String.format("- 类型：%s，值：%s\n", entity.type(), entity.value()));
        }
        sb.append(String.format("- 查询意图：%s", entities.intent()));
        return sb.toString();
    }

    /**
     * 验证 SPARQL 是否有效
     */
    private boolean isValidSPARQL(String sparql) {
        if (sparql == null || sparql.isEmpty()) {
            return false;
        }

        String normalized = sparql.toUpperCase().trim();
        return normalized.startsWith("SELECT") &&
                (normalized.contains("WHERE") || normalized.contains("{"));
    }
}
