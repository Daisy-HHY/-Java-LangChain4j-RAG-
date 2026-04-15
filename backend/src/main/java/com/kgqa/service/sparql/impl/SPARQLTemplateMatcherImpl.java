package com.kgqa.service.sparql.impl;

import com.kgqa.kg.MedicalOntology;
import com.kgqa.service.qa.MedicalEntityExtractor;
import com.kgqa.service.sparql.SPARQLTemplateMatcher;
import org.apache.jena.rdf.model.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SPARQL 模板匹配器实现
 * 根据问题模板匹配生成 SPARQL 查询
 *
 * 支持的查询类型：
 * - 适应症查询（药物→疾病）
 * - 症状查询（疾病→症状）
 * - 治疗查询（疾病→药物）
 * - 副作用/禁忌查询
 * - 多跳查询（药物→疾病→症状）
 * - 诊断/检查查询
 * - 科室归属查询
 */
@Service
public class SPARQLTemplateMatcherImpl implements SPARQLTemplateMatcher {

    // SPARQL 模板列表
    private final List<SPARQLTemplate> templates;

    // 实体类型 → 本体 Resource 映射
    private static final Resource DISEASE = MedicalOntology.DISEASE;
    private static final Resource SYMPTOM = MedicalOntology.SYMPTOM;
    private static final Resource DRUG = MedicalOntology.DRUG;
    private static final Resource DEPARTMENT = MedicalOntology.DEPARTMENT;
    private static final Resource EXAMINATION = MedicalOntology.EXAMINATION;
    private static final Resource BODY_PART = MedicalOntology.BODY_PART;
    private static final Resource TREATMENT = MedicalOntology.TREATMENT;

    public SPARQLTemplateMatcherImpl() {
        this.templates = new ArrayList<>();
        initTemplates();
    }

    // Wikidata 属性 URI 常量
    private static final String WDT_P780 = "http://www.wikidata.org/prop/direct/P780";     // 症状
    private static final String WDT_P2176 = "http://www.wikidata.org/prop/direct/P2176";   // 治疗/药物
    private static final String WDT_P828 = "http://www.wikidata.org/prop/direct/P828";    // 病因
    private static final String WDT_P1542 = "http://www.wikidata.org/prop/direct/P1542";   // 并发症
    private static final String WDT_P923 = "http://www.wikidata.org/prop/direct/P923";     // 检查
    private static final String WDT_P1995 = "http://www.wikidata.org/prop/direct/P1995";   // 科室
    private static final String WDT_P927 = "http://www.wikidata.org/prop/direct/P927";     // 部位

    /**
     * 初始化 SPARQL 模板
     * 使用 rdfs:label 查询，支持中文简繁体
     * 模板使用 {entityLabel} 占位符，运行时替换为具体实体名称
     */
    private void initTemplates() {
        // ===== 药物相关查询 =====

        // 适应症查询 - 药物的适应症
        templates.add(new SPARQLTemplate(
                "(.+)的适应症",
                "SELECT ?answer WHERE { ?entity <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?entity <" + WDT_P2176 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "药物",
                "适应症"
        ));

        // 适应症查询 - 什么药可以治疗
        templates.add(new SPARQLTemplate(
                "(?:哪些?|什么)药(?:物)?(?:可以)?治疗(.+)",
                "SELECT ?drugLabel WHERE { ?drug <" + WDT_P2176 + "> ?disease . ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?drug <http://www.w3.org/2000/01/rdf-schema#label> ?drugLabel }",
                "疾病",
                "适应症"
        ));

        // 副作用查询
        templates.add(new SPARQLTemplate(
                "(.+)的?副作用",
                "SELECT ?answer WHERE { ?entity <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?entity <" + WDT_P780 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "药物",
                "副作用"
        ));

        // 禁忌查询
        templates.add(new SPARQLTemplate(
                "(.+)的?禁忌(?:症)?",
                "SELECT ?answer WHERE { ?entity <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?entity <" + WDT_P2176 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "药物",
                "禁忌"
        ));

        // 用法查询
        templates.add(new SPARQLTemplate(
                "(.+)的?用?法",
                "SELECT ?answer WHERE { ?entity <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?entity <" + WDT_P2176 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "药物",
                "用法"
        ));

        // 剂量查询
        templates.add(new SPARQLTemplate(
                "(.+)的?剂量",
                "SELECT ?answer WHERE { ?entity <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?entity <" + WDT_P2176 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "药物",
                "剂量"
        ));

        // ===== 疾病相关查询 =====

        // 症状查询 - 疾病的症状
        templates.add(new SPARQLTemplate(
                "(.+)的?症状",
                "SELECT ?answerLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P780 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "疾病",
                "症状"
        ));

        // 症状查询 - 什么疾病有症状
        templates.add(new SPARQLTemplate(
                "哪些疾病(?:会)?(?:出现|有)(.+?)症状",
                "SELECT ?diseaseLabel WHERE { ?disease <" + WDT_P780 + "> ?symptom . ?symptom <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <http://www.w3.org/2000/01/rdf-schema#label> ?diseaseLabel }",
                "症状",
                "症状"
        ));

        // 治疗查询 - 疾病的治疗方法
        templates.add(new SPARQLTemplate(
                "(.+)的?治疗方法",
                "SELECT ?answerLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P2176 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "疾病",
                "治疗"
        ));

        // 治疗查询 - 疾病用什么药治疗
        templates.add(new SPARQLTemplate(
                "(.+)用什么药",
                "SELECT ?drugLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P2176 + "> ?drug . ?drug <http://www.w3.org/2000/01/rdf-schema#label> ?drugLabel }",
                "疾病",
                "治疗"
        ));

        // 诊断查询
        templates.add(new SPARQLTemplate(
                "(.+)的?诊断(?:方法)?",
                "SELECT ?answerLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P923 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "疾病",
                "检查"
        ));

        // 诊断查询 - 如何诊断
        templates.add(new SPARQLTemplate(
                "如何诊断(.+)",
                "SELECT ?answerLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P923 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "疾病",
                "检查"
        ));

        // 病因查询
        templates.add(new SPARQLTemplate(
                "(.+)的?病因",
                "SELECT ?answerLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P828 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "疾病",
                "病因"
        ));

        // 并发症查询
        templates.add(new SPARQLTemplate(
                "(.+)的?并发症",
                "SELECT ?answerLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P1542 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "疾病",
                "并发症"
        ));

        // 科室查询
        templates.add(new SPARQLTemplate(
                "(.+)属于?哪个科室",
                "SELECT ?answerLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P1995 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "疾病",
                "科室"
        ));

        // 部位查询
        templates.add(new SPARQLTemplate(
                "(.+)位于?哪里",
                "SELECT ?answerLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P927 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel }",
                "疾病",
                "部位"
        ));

        // ===== 多跳查询 =====

        // 药物适应症疾病的治疗方法（药物→适应症→治疗）
        templates.add(new SPARQLTemplate(
                "(.+)治疗的疾病怎么治",
                "SELECT ?treatmentLabel WHERE { ?disease <" + WDT_P2176 + "> ?drug . ?drug <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P2176 + "> ?treatment . ?treatment <http://www.w3.org/2000/01/rdf-schema#label> ?treatmentLabel }",
                "药物",
                "治疗"
        ));

        // 药物适应症疾病的症状（药物→适应症→症状）
        templates.add(new SPARQLTemplate(
                "(.+)治疗的疾病有什么症状",
                "SELECT ?symptomLabel WHERE { ?disease <" + WDT_P2176 + "> ?drug . ?drug <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P780 + "> ?symptom . ?symptom <http://www.w3.org/2000/01/rdf-schema#label> ?symptomLabel }",
                "药物",
                "症状"
        ));

        // ===== 列表查询 =====

        // 所有症状
        templates.add(new SPARQLTemplate(
                "(.+)的?所有症状",
                "SELECT ?answerLabel WHERE { ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?disease <" + WDT_P780 + "> ?answer . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel } LIMIT 50",
                "疾病",
                "症状"
        ));

        // 所有药物
        templates.add(new SPARQLTemplate(
                "(.+)的?所有药物",
                "SELECT ?answerLabel WHERE { ?answer <" + WDT_P2176 + "> ?disease . ?disease <http://www.w3.org/2000/01/rdf-schema#label> \"{entityLabel}\"@zh-hans . ?answer <http://www.w3.org/2000/01/rdf-schema#label> ?answerLabel } LIMIT 50",
                "疾病",
                "治疗"
        ));
    }

    /**
     * 匹配模板并生成 SPARQL
     */
    @Override
    public MatchResult match(String question, MedicalEntityExtractor.ExtractionResult entities) {
        if (question == null || question.isEmpty()) {
            return null;
        }

        // 查找匹配的模板
        for (SPARQLTemplate template : templates) {
            Pattern pattern = Pattern.compile(template.pattern());
            Matcher matcher = pattern.matcher(question);

            if (matcher.find()) {
                // 替换 SPARQL 中的占位符
                String sparql = template.sparql();

                // 处理 label 查询模板
                if (sparql.contains("{entityLabel}")) {
                    String entityValue = extractEntityValue(matcher, entities, template.entityType());

                    if (entityValue != null) {
                        // 清理实体名称（去除末尾的"的"等字符）
                        String cleanEntity = entityValue.trim().replaceAll("的$", "");
                        sparql = sparql.replace("{entityLabel}", cleanEntity);
                        return new MatchResult(sparql, template.predicate(), cleanEntity);
                    }
                }
                // 处理多实体模板（如比较查询）
                else if (sparql.contains("{entity1}") && sparql.contains("{entity2}")) {
                    List<String> entityValues = extractMultipleEntityValues(matcher, entities);

                    if (entityValues != null && entityValues.size() >= 2) {
                        sparql = sparql.replace("{entity1}", createEntityUri(entityValues.get(0), "药物"));
                        sparql = sparql.replace("{entity2}", createEntityUri(entityValues.get(1), "药物"));
                        return new MatchResult(sparql, template.predicate(), entityValues.get(0));
                    }
                }
            }
        }

        return null; // 没有匹配的模板
    }

    /**
     * 根据实体类型和名称创建 label 查询模式
     * 使用 rdfs:label 查找实体，而不是直接用 URI
     */
    private String createLabelQuery(String entityName, String entityType) {
        // 清理实体名称
        String cleanName = entityName.trim()
                .replaceAll("[\\s/()]+", "_")
                .replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "");
        // 使用 label 查询，支持中文简繁体
        return String.format(
            "?entity <http://www.w3.org/2000/01/rdf-schema#label> \"%s\"@zh-hans . ?entity <http://www.wikidata.org/prop/direct/P31> <http://www.wikidata.org/entity/Q12136>",
            cleanName
        );
    }

    /**
     * 根据实体类型和名称创建正确的 URI
     * @deprecated 使用 createLabelQuery 进行 label 查询更可靠
     */
    @Deprecated
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
     * 提取多个实体值（用于多实体查询）
     */
    private List<String> extractMultipleEntityValues(Matcher matcher,
                                                     MedicalEntityExtractor.ExtractionResult entities) {
        List<String> values = new ArrayList<>();

        // 从正则匹配中提取
        for (int i = 1; i <= matcher.groupCount() && i <= 2; i++) {
            String extracted = matcher.group(i);
            if (extracted != null && !extracted.trim().isEmpty()) {
                values.add(extracted.trim());
            }
        }

        // 如果正则匹配不够，从实体列表补充
        if (values.size() < 2 && entities != null && entities.entities() != null) {
            for (MedicalEntityExtractor.MedicalEntity entity : entities.entities()) {
                if (values.size() >= 2) break;
                // 检查是否已存在
                boolean found = values.stream().anyMatch(v -> v.contains(entity.value()));
                if (!found) {
                    values.add(entity.value());
                }
            }
        }

        return values.size() >= 2 ? values : null;
    }

    /**
     * 从正则匹配结果或实体中提取实体值
     */
    private String extractEntityValue(Matcher matcher,
                                     MedicalEntityExtractor.ExtractionResult entities,
                                     String entityType) {
        // 首先从正则匹配的组中提取
        if (matcher.groupCount() >= 1) {
            String extracted = matcher.group(1).trim();
            // 去除捕获值末尾的"的"等噪点字符
            extracted = extracted.replaceAll("的$", "");
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }

        // 如果正则没有匹配到，从实体列表中查找
        if (entities != null && entities.entities() != null) {
            for (MedicalEntityExtractor.MedicalEntity entity : entities.entities()) {
                if (entity.type().equals(entityType)) {
                    return entity.value();
                }
            }
        }

        return null;
    }
}
