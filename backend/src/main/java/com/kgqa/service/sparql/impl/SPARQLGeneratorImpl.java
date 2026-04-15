package com.kgqa.service.sparql.impl;

import com.kgqa.kg.MedicalOntology;
import com.kgqa.service.qa.MedicalEntityExtractor;
import com.kgqa.service.sparql.SPARQLGenerator;
import com.kgqa.service.sparql.SPARQLValidator;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SPARQL 生成器实现
 * 当模板匹配失败时，使用 LLM 根据问题生成 SPARQL
 */
@Service
public class SPARQLGeneratorImpl implements SPARQLGenerator {

    private static final Logger log = LoggerFactory.getLogger(SPARQLGeneratorImpl.class);

    private final ChatModel chatModel;
    private final SPARQLValidator validator;

    // SPARQL 生成提示词（增强版 - 包含完整本体定义和多跳查询支持）
    private static final String SPARQL_GENERATION_PROMPT = """
            你是一个医疗知识图谱 SPARQL 查询生成专家。

            ## 知识图谱本体定义

            【实体类型和 URI】
            - Disease（疾病）：URI 如 <http://kgqa.com/data#dis_冠心病>
            - Symptom（症状/体征）：URI 如 <http://kgqa.com/data#sym_胸痛>
            - Drug（药物）：URI 如 <http://kgqa.com/data#dru_阿司匹林>
            - Examination（检查项目）：URI 如 <http://kgqa.com/data#exa_心电图>
            - Department（科室）：URI 如 <http://kgqa.com/data#dep_心内科>
            - BodyPart（身体部位/器官）：URI 如 <http://kgqa.com/data#bod_心脏>

            【关系类型和 URI】（主体 <关系> 客体）
            疾病相关：
            - <http://kgqa.com/medical#hasSymptom>（症状）
            - <http://kgqa.com/medical#treatedBy>（治疗）
            - <http://kgqa.com/medical#requiresExam>（检查）
            - <http://kgqa.com/medical#locatedIn>（部位）
            - <http://kgqa.com/medical#belongsToDept>（科室）
            - <http://kgqa.com/medical#causedBy>（病因）
            - <http://kgqa.com/medical#complicationOf>（并发症）

            药物相关：
            - <http://kgqa.com/medical#indication>（适应症）
            - <http://kgqa.com/medical#sideEffect>（副作用）
            - <http://kgqa.com/medical#contraindicatedWith>（禁忌）

            ## SPARQL 语法规范

            【基础查询】
            SELECT ?x WHERE { <主体URI> <关系URI> ?x }

            【反向查询】
            SELECT ?x WHERE { ?x <关系URI> <客体URI> }

            【多跳查询】
            SELECT ?result WHERE {
              <主体URI> <关系1URI> ?中间 .
              ?中间 <关系2URI> ?result
            }

            ## 生成规则

            1. 只返回 SELECT 查询，不要返回其他内容
            2. 必须使用完整的 URI 格式，如 <http://kgqa.com/data#dis_冠心病>
            3. 关系必须使用完整的 URI，如 <http://kgqa.com/medical#hasSymptom>
            4. 列表查询添加 LIMIT 限制（如 LIMIT 20）
            5. 只生成有效的 SPARQL SELECT 语句

            ## 用户问题

            %s

            ## 已识别的实体

            %s

            ## SPARQL 查询（只返回查询语句）：
            """;

    // 简化版本的 SPARQL 生成
    private static final String SIMPLE_GENERATION_PROMPT = """
            请根据以下信息生成 SPARQL 查询：

            问题：%s
            实体类型：%s
            实体值：%s
            查询意图：%s

            请生成只返回 SPARQL 查询语句。
            """;

    // 实体类型 → 本体 Resource 映射
    private static final Resource DISEASE = MedicalOntology.DISEASE;
    private static final Resource SYMPTOM = MedicalOntology.SYMPTOM;
    private static final Resource DRUG = MedicalOntology.DRUG;
    private static final Resource DEPARTMENT = MedicalOntology.DEPARTMENT;
    private static final Resource EXAMINATION = MedicalOntology.EXAMINATION;
    private static final Resource BODY_PART = MedicalOntology.BODY_PART;
    private static final Resource TREATMENT = MedicalOntology.TREATMENT;

    public SPARQLGeneratorImpl(ChatModel chatModel, SPARQLValidator validator) {
        this.chatModel = chatModel;
        this.validator = validator;
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
            SPARQLValidator.ValidationResult validation = validator.validate(response);
            if (validation.valid()) {
                log.debug("LLM 生成的 SPARQL 通过验证: {}", response);
                return response;
            }

            log.warn("LLM 生成的 SPARQL 验证失败: {} - 原始: {}", validation.message(), response);

            // 如果 LLM 生成的格式不正确，尝试简单生成
            return generateSimple(question, entities);
        } catch (Exception e) {
            // LLM 调用失败时返回 null，让系统 fallback 到 RAG
            log.error("LLM 生成 SPARQL 失败: {}", e.getMessage());
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
            // 检测查询类型
            QueryType queryType = detectQueryType(question, entities);

            // 多跳查询
            if (queryType == QueryType.MULTI_HOP) {
                String multiHop = generateMultiHopQuery(question, entities);
                if (multiHop != null) {
                    return multiHop;
                }
            }

            // 获取第一个实体
            MedicalEntityExtractor.MedicalEntity entity = entities.entities().get(0);
            String entityType = entity.type();
            String entityValue = entity.value();
            String intent = entities.intent();

            // 根据意图选择谓词 URI
            String predicateUri = selectPredicateUri(intent, entityType);

            if (predicateUri != null) {
                // 使用正确的 URI
                String entityUri = createEntityUri(entityValue, entityType);

                String sparql;
                // 构建简单查询
                if (intent.contains("哪些") || intent.contains("列表")) {
                    // 反向查询
                    sparql = String.format(
                            "SELECT ?answer WHERE { ?answer <%s> <%s> } LIMIT 20",
                            predicateUri, entityUri
                    );
                } else {
                    // 正向查询
                    sparql = String.format(
                            "SELECT ?answer WHERE { <%s> <%s> ?answer } LIMIT 10",
                            entityUri, predicateUri
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
     * 根据实体类型和名称创建正确的 URI
     */
    private String createEntityUri(String entityName, String entityType) {
        Resource typeResource = getTypeResource(entityType);
        return MedicalOntology.createEntityResource(entityName, typeResource).getURI();
    }

    /**
     * 根据实体类型字符串获取本体 Resource
     */
    private Resource getTypeResource(String entityType) {
        return switch (entityType) {
            case "疾病" -> DISEASE;
            case "症状" -> SYMPTOM;
            case "药物" -> DRUG;
            case "科室" -> DEPARTMENT;
            case "检查" -> EXAMINATION;
            case "部位" -> BODY_PART;
            case "治疗" -> TREATMENT;
            default -> DISEASE;
        };
    }

    /**
     * 根据意图选择谓词 URI
     */
    private String selectPredicateUri(String intent, String entityType) {
        if (intent.contains("适应症")) {
            return entityType.equals("药物") ? MedicalOntology.INDICATION.getURI() : null;
        }
        if (intent.contains("副作用")) {
            return MedicalOntology.SIDE_EFFECT.getURI();
        }
        if (intent.contains("禁忌")) {
            return MedicalOntology.CONTRAINDICATED_WITH.getURI();
        }
        if (intent.contains("症状")) {
            return MedicalOntology.HAS_SYMPTOM.getURI();
        }
        if (intent.contains("治疗")) {
            return MedicalOntology.TREATED_BY.getURI();
        }
        if (intent.contains("诊断") || intent.contains("检查")) {
            return MedicalOntology.REQUIRES_EXAM.getURI();
        }
        if (intent.contains("病因")) {
            return MedicalOntology.CAUSED_BY.getURI();
        }
        if (intent.contains("科室")) {
            return MedicalOntology.BELONGS_TO_DEPT.getURI();
        }
        if (intent.contains("部位")) {
            return MedicalOntology.LOCATED_IN.getURI();
        }
        if (intent.contains("并发症")) {
            return MedicalOntology.COMPLICATION_OF.getURI();
        }

        // 默认谓词
        if (entityType.equals("药物")) {
            return MedicalOntology.INDICATION.getURI();
        } else if (entityType.equals("疾病")) {
            return MedicalOntology.HAS_SYMPTOM.getURI();
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
            String uri = createEntityUri(entity.value(), entity.type());
            sb.append(String.format("- 类型：%s，值：%s，URI：%s\n", entity.type(), entity.value(), uri));
        }
        sb.append(String.format("- 查询意图：%s", entities.intent()));
        return sb.toString();
    }

    /**
     * 检测是否需要多跳查询
     */
    private boolean isMultiHopQuery(String question) {
        String q = question.toLowerCase();
        return (q.contains("治疗的疾病") && q.contains("症状")) ||
               (q.contains("的药物") && q.contains("适应症")) ||
               (q.contains("哪些药") && q.contains("症状")) ||
               (q.contains("治疗的疾病") && q.contains("治疗"));
    }

    /**
     * 检测查询类型
     */
    private QueryType detectQueryType(String question, MedicalEntityExtractor.ExtractionResult entities) {
        String q = question.toLowerCase();
        int entityCount = entities != null ? entities.entities().size() : 0;

        // 多跳查询检测
        if (isMultiHopQuery(question)) {
            return QueryType.MULTI_HOP;
        }

        // 单跳查询
        if (entityCount == 1) {
            return QueryType.SINGLE_HOP;
        }

        // 反向查询
        if (q.contains("哪些") || q.contains("什么药") || q.contains("哪些疾病")) {
            return QueryType.REVERSE_LOOKUP;
        }

        // 列表查询
        if (q.contains("所有") || q.contains("列表")) {
            return QueryType.LIST;
        }

        // 聚合查询
        if (q.contains("多少") || q.contains("数量") || q.contains("统计")) {
            return QueryType.AGGREGATE;
        }

        return QueryType.SINGLE_HOP;
    }

    /**
     * 生成多跳查询 SPARQL
     */
    private String generateMultiHopQuery(String question, MedicalEntityExtractor.ExtractionResult entities) {
        if (entities == null || entities.entities().isEmpty()) {
            return null;
        }

        String q = question.toLowerCase();

        // 获取实体
        MedicalEntityExtractor.MedicalEntity entity = entities.entities().get(0);
        String entityValue = entity.value();
        String entityType = entity.type();
        String entityUri = createEntityUri(entityValue, entityType);

        // 药物 → 疾病 → 症状
        if (q.contains("治疗的疾病") && (q.contains("症状") || q.contains("怎么治"))) {
            return String.format(
                    "SELECT ?result WHERE { " +
                            "?disease <%s> <%s> . " +
                            "?disease <%s> ?result " +
                            "} LIMIT 20",
                    MedicalOntology.INDICATION.getURI(), entityUri,
                    MedicalOntology.HAS_SYMPTOM.getURI()
            );
        }

        // 疾病 → 药物
        if ((q.contains("用什么药") || q.contains("怎么治疗")) && entity.type().equals("疾病")) {
            return String.format(
                    "SELECT ?drug WHERE { " +
                            "<%s> <%s> ?drug " +
                            "} LIMIT 20",
                    entityUri, MedicalOntology.TREATED_BY.getURI()
            );
        }

        // 疾病 → 检查
        if (q.contains("诊断") && entity.type().equals("疾病")) {
            return String.format(
                    "SELECT ?exam WHERE { " +
                            "<%s> <%s> ?exam " +
                            "} LIMIT 20",
                    entityUri, MedicalOntology.REQUIRES_EXAM.getURI()
            );
        }

        return null;
    }

    /**
     * 查询类型枚举
     */
    private enum QueryType {
        SINGLE_HOP,      // 单跳查询
        MULTI_HOP,       // 多跳查询
        REVERSE_LOOKUP,  // 反向查询
        LIST,            // 列表查询
        AGGREGATE        // 聚合查询
    }
}
