package com.kgqa.kg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kgqa.data.MedQaRecord;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 三元组抽取器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripleExtractor {

    private final ChatModel llm;
    private final ObjectMapper objectMapper;

    /**
     * 三元组数据结构
     */
    public record Triple(
            String subject,
            String subjectType,
            String predicate,
            String object,
            String objectType,
            double confidence
    ) {}

    private static final String EXTRACTION_PROMPT = """
            你是一个医疗知识图谱三元组抽取专家。

            从以下医学题目中抽取实体关系三元组，严格按 JSON 数组格式返回。

            允许的实体类型（subjectType / objectType）：
            - Disease（疾病）
            - Symptom（症状/体征）
            - Drug（药物/治疗方案）
            - Examination（检查项目）
            - BodyPart（身体部位/器官）
            - Department（科室）

            允许的关系（predicate）：
            - hasSymptom（疾病→症状）
            - treatedBy / indication（疾病→药物）
            - requiresExam（疾病→检查项目）
            - locatedIn（疾病→身体部位）
            - belongsToDept（疾病→科室）
            - sideEffect（药物→副作用）
            - causedBy（疾病→病因）
            - complicationOf（并发症→疾病）

            返回格式（只返回 JSON，不要任何解释）：
            [
              {
                "subject": "实体名称",
                "subjectType": "类型",
                "predicate": "关系",
                "object": "实体名称",
                "objectType": "类型",
                "confidence": 0.9
              }
            ]

            如果无法抽取到有效三元组，返回空数组 []。

            医学题目：
            %s
            """;

    /**
     * 从单条 MedQA 记录中抽取三元组
     */
    public List<Triple> extract(MedQaRecord record) {
        String prompt = String.format(EXTRACTION_PROMPT, record.toExtractionText());

        try {
            String response = llm.chat(prompt);
            return parseTriples(response);
        } catch (Exception e) {
            log.warn("三元组抽取失败，问题: {}",
                    record.getQuestion().substring(0, Math.min(50, record.getQuestion().length())));
            return List.of();
        }
    }

    /**
     * 批量抽取，并过滤低置信度三元组
     */
    public List<Triple> extractBatch(List<MedQaRecord> records, double minConfidence) {
        List<Triple> all = new ArrayList<>();
        for (MedQaRecord record : records) {
            List<Triple> triples = extract(record);
            triples.stream()
                    .filter(t -> t.confidence() >= minConfidence)
                    .forEach(all::add);
        }
        return all;
    }

    private List<Triple> parseTriples(String json) {
        List<Triple> result = new ArrayList<>();
        try {
            String cleaned = json.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("\\s*```$", "")
                    .trim();

            JsonNode array = objectMapper.readTree(cleaned);
            if (!array.isArray()) return result;

            for (JsonNode node : array) {
                try {
                    Triple triple = new Triple(
                            node.path("subject").asText(),
                            node.path("subjectType").asText(),
                            node.path("predicate").asText(),
                            node.path("object").asText(),
                            node.path("objectType").asText(),
                            node.path("confidence").asDouble(0.8)
                    );
                    if (isValidTriple(triple)) {
                        result.add(triple);
                    }
                } catch (Exception e) {
                    log.debug("跳过无效三元组节点: {}", node);
                }
            }
        } catch (Exception e) {
            log.warn("三元组 JSON 解析失败，原始响应: {}",
                    json.substring(0, Math.min(200, json.length())));
        }
        return result;
    }

    private boolean isValidTriple(Triple t) {
        return t.subject() != null && !t.subject().isBlank()
            && t.predicate() != null && !t.predicate().isBlank()
            && t.object() != null && !t.object().isBlank()
            && t.subjectType() != null && !t.subjectType().isBlank()
            && t.objectType() != null && !t.objectType().isBlank();
    }
}
