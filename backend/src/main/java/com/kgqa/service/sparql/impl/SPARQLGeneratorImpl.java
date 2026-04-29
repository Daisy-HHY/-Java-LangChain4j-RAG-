package com.kgqa.service.sparql.impl;

import com.kgqa.kg.MedicalOntology;
import com.kgqa.service.qa.MedicalEntityExtractor;
import com.kgqa.service.sparql.SPARQLGenerator;
import com.kgqa.service.sparql.SPARQLValidator;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * kgdrug SPARQL 生成器。
 */
@Service
public class SPARQLGeneratorImpl implements SPARQLGenerator {

    private static final Logger log = LoggerFactory.getLogger(SPARQLGeneratorImpl.class);

    private static final String DISEASE_NAME = MedicalOntology.DISEASE_NAME.getURI();
    private static final String SYMPTOM_NAME = MedicalOntology.SYMPTOM_NAME.getURI();
    private static final String DRUG_NAME = MedicalOntology.DRUG_NAME.getURI();
    private static final String HAS_SYMPTOM = MedicalOntology.HAS_SYMPTOM.getURI();
    private static final String NEED_CURE = MedicalOntology.NEED_CURE.getURI();
    private static final String RELATED_DISEASE = MedicalOntology.RELATED_DISEASE.getURI();
    private static final String CURE = MedicalOntology.CURE.getURI();
    private static final String CAUSE = MedicalOntology.CAUSE.getURI();
    private static final String COMPLICATION = MedicalOntology.COMPLICATION.getURI();
    private static final String TREATMENT = MedicalOntology.TREATMENT.getURI();
    private static final String OVERVIEW = MedicalOntology.OVERVIEW.getURI();
    private static final String PREVENTION = MedicalOntology.PREVENTION.getURI();

    private final ChatModel chatModel;
    private final SPARQLValidator validator;

    private static final String SPARQL_GENERATION_PROMPT = """
            你是 kgdrug 医疗知识图谱 SPARQL 查询生成专家。

            只能使用以下 kgdrug 本体 URI，不允许使用其他本体。

            实体名称属性：
            - 疾病名称：<http://www.kgdrug.com#jibingname>
            - 症状名称：<http://www.kgdrug.com#zzname>
            - 药品名称：<http://www.kgdrug.com#proname>

            关系/属性：
            - 疾病-症状：<http://www.kgdrug.com#haszhengzhuang>
            - 疾病-用药：<http://www.kgdrug.com#needcure>
            - 药品-治疗疾病：<http://www.kgdrug.com#cure>
            - 症状-相关疾病：<http://www.kgdrug.com#relatedisease>
            - 病因：<http://www.kgdrug.com#bingyin>
            - 并发症：<http://www.kgdrug.com#bingfazheng>
            - 治疗：<http://www.kgdrug.com#zhiliao>
            - 概述：<http://www.kgdrug.com#gaishu>
            - 预防：<http://www.kgdrug.com#yufang>

            查询必须通过中文名称属性定位实体，例如：
            SELECT ?answer WHERE {
              ?disease <http://www.kgdrug.com#jibingname> "胃窦炎" .
              ?disease <http://www.kgdrug.com#needcure> ?drug .
              ?drug <http://www.kgdrug.com#proname> ?answer
            } LIMIT 20

            只返回 SELECT 查询，不要返回解释、Markdown 或代码块。

            用户问题：%s

            已识别实体：
            %s
            """;

    public SPARQLGeneratorImpl(ChatModel chatModel, SPARQLValidator validator) {
        this.chatModel = chatModel;
        this.validator = validator;
    }

    @Override
    public String generate(String question, MedicalEntityExtractor.ExtractionResult entities) {
        String simple = generateSimple(question, entities);
        if (simple != null) {
            return simple;
        }

        try {
            String prompt = String.format(SPARQL_GENERATION_PROMPT, question, formatEntityInfo(entities));
            String response = stripCodeFence(chatModel.chat(prompt).trim());
            SPARQLValidator.ValidationResult validation = validator.validate(response);
            if (validation.valid() && isKgdrugOnly(response)) {
                log.debug("LLM 生成 kgdrug SPARQL: {}", response);
                return response;
            }
            log.warn("LLM 生成的 SPARQL 不符合 kgdrug 约束: {} - 原始: {}", validation.message(), response);
        } catch (Exception e) {
            log.error("LLM 生成 SPARQL 失败: {}", e.getMessage());
        }

        return null;
    }

    @Override
    public String generateSimple(String question, MedicalEntityExtractor.ExtractionResult entities) {
        if (question == null || entities == null || entities.entities().isEmpty()) {
            return null;
        }

        MedicalEntityExtractor.MedicalEntity entity = entities.entities().get(0);
        String entityValue = escapeLiteral(entity.value());
        String entityType = entity.type();
        String q = question.toLowerCase();

        if ("疾病".equals(entityType)) {
            if (q.contains("症状")) {
                return diseaseToSymptomQuery(entityValue);
            }
            if (q.contains("用什么药") || q.contains("什么药") || q.contains("药物")) {
                return diseaseToDrugQuery(entityValue);
            }
            if (q.contains("病因") || q.contains("原因")) {
                return diseasePropertyQuery(entityValue, CAUSE, 10);
            }
            if (q.contains("并发症")) {
                return diseasePropertyQuery(entityValue, COMPLICATION, 10);
            }
            if (q.contains("治疗")) {
                return diseasePropertyQuery(entityValue, TREATMENT, 10);
            }
            if (q.contains("预防")) {
                return diseasePropertyQuery(entityValue, PREVENTION, 10);
            }
            if (q.contains("概述") || q.contains("介绍") || q.contains("简介")) {
                return diseasePropertyQuery(entityValue, OVERVIEW, 5);
            }
        }

        if ("症状".equals(entityType) && (q.contains("哪些疾病") || q.contains("什么疾病"))) {
            return symptomToDiseaseQuery(entityValue);
        }

        if ("药物".equals(entityType) && (q.contains("适应症") || q.contains("治疗什么") || q.contains("治什么"))) {
            return drugToDiseaseQuery(entityValue);
        }

        return null;
    }

    private String diseaseToSymptomQuery(String diseaseName) {
        return "SELECT ?answer WHERE { ?disease <" + DISEASE_NAME + "> \"" + diseaseName + "\" . " +
                "?disease <" + HAS_SYMPTOM + "> ?symptom . " +
                "?symptom <" + SYMPTOM_NAME + "> ?answer } LIMIT 30";
    }

    private String diseaseToDrugQuery(String diseaseName) {
        return "SELECT ?answer WHERE { ?disease <" + DISEASE_NAME + "> \"" + diseaseName + "\" . " +
                "?disease <" + NEED_CURE + "> ?drug . " +
                "?drug <" + DRUG_NAME + "> ?answer } LIMIT 30";
    }

    private String symptomToDiseaseQuery(String symptomName) {
        return "SELECT ?answer WHERE { ?symptom <" + SYMPTOM_NAME + "> \"" + symptomName + "\" . " +
                "?disease <" + HAS_SYMPTOM + "> ?symptom . " +
                "?disease <" + DISEASE_NAME + "> ?answer } LIMIT 30";
    }

    private String drugToDiseaseQuery(String drugName) {
        return "SELECT ?answer WHERE { ?drug <" + DRUG_NAME + "> \"" + drugName + "\" . " +
                "?drug <" + CURE + "> ?disease . " +
                "?disease <" + DISEASE_NAME + "> ?answer } LIMIT 30";
    }

    private String diseasePropertyQuery(String diseaseName, String propertyUri, int limit) {
        return "SELECT ?answer WHERE { ?disease <" + DISEASE_NAME + "> \"" + diseaseName + "\" . " +
                "?disease <" + propertyUri + "> ?answer } LIMIT " + limit;
    }

    private String formatEntityInfo(MedicalEntityExtractor.ExtractionResult entities) {
        if (entities == null || entities.entities().isEmpty()) {
            return "无";
        }

        StringBuilder sb = new StringBuilder();
        for (MedicalEntityExtractor.MedicalEntity entity : entities.entities()) {
            sb.append("- 类型：").append(entity.type())
                    .append("，值：").append(entity.value())
                    .append('\n');
        }
        sb.append("- 查询意图：").append(entities.intent());
        return sb.toString();
    }

    private boolean isKgdrugOnly(String sparql) {
        return Pattern.compile("<(http[^>]+)>")
                .matcher(sparql)
                .results()
                .map(match -> match.group(1))
                .allMatch(uri -> uri.startsWith(MedicalOntology.KGDRUG_NS));
    }

    private String stripCodeFence(String response) {
        return response.replaceAll("^```(?:sparql)?\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
    }

    private String escapeLiteral(String value) {
        return value == null ? "" : value.trim().replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
