package com.kgqa.service.sparql.impl;

import com.kgqa.service.qa.MedicalEntityExtractor;
import com.kgqa.service.sparql.SPARQLTemplateMatcher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SPARQL 模板匹配器实现
 * 根据问题模板匹配生成 SPARQL 查询
 */
@Service
public class SPARQLTemplateMatcherImpl implements SPARQLTemplateMatcher {

    // SPARQL 模板列表
    private final List<SPARQLTemplate> templates;

    public SPARQLTemplateMatcherImpl() {
        this.templates = new ArrayList<>();
        initTemplates();
    }

    /**
     * 初始化 SPARQL 模板
     */
    private void initTemplates() {
        // 适应症查询 - 药物的适应症
        templates.add(new SPARQLTemplate(
                "(.+)的适应症.*",
                "SELECT ?answer WHERE { <{entity}> <适应症> ?answer }",
                "药物",
                "适应症"
        ));

        // 适应症查询 - 什么药可以治疗
        templates.add(new SPARQLTemplate(
                "哪些药(?:物)?可以治疗(.+)",
                "SELECT ?drug WHERE { ?drug <适应症> <{entity}> }",
                "疾病",
                "适应症"
        ));

        // 副作用查询
        templates.add(new SPARQLTemplate(
                "(.+)有什么副作用",
                "SELECT ?answer WHERE { <{entity}> <副作用> ?answer }",
                "药物",
                "副作用"
        ));

        // 禁忌查询
        templates.add(new SPARQLTemplate(
                "(.+)有什么禁忌",
                "SELECT ?answer WHERE { <{entity}> <禁忌> ?answer }",
                "药物",
                "禁忌"
        ));

        // 症状查询 - 疾病的症状
        templates.add(new SPARQLTemplate(
                "(.+)有什么症状",
                "SELECT ?answer WHERE { <{entity}> <症状> ?answer }",
                "疾病",
                "症状"
        ));

        // 用法查询
        templates.add(new SPARQLTemplate(
                "(.+)怎么使用",
                "SELECT ?answer WHERE { <{entity}> <用法> ?answer }",
                "药物",
                "用法"
        ));

        // 剂量查询
        templates.add(new SPARQLTemplate(
                "(.+)的剂量.*",
                "SELECT ?answer WHERE { <{entity}> <剂量> ?answer }",
                "药物",
                "剂量"
        ));

        // 诊断查询
        templates.add(new SPARQLTemplate(
                "(.+)怎么诊断",
                "SELECT ?answer WHERE { <{entity}> <诊断> ?answer }",
                "疾病",
                "诊断"
        ));

        // 治疗查询 - 疾病的治疗方法
        templates.add(new SPARQLTemplate(
                "(.+)怎么治疗",
                "SELECT ?answer WHERE { <{entity}> <治疗> ?answer }",
                "疾病",
                "治疗"
        ));

        // 病因查询
        templates.add(new SPARQLTemplate(
                "(.+)的病因.*",
                "SELECT ?answer WHERE { <{entity}> <病因> ?answer }",
                "疾病",
                "病因"
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
                // 提取实体
                String entityValue = extractEntityValue(matcher, entities, template.entityType());

                if (entityValue != null) {
                    // 替换 SPARQL 中的占位符
                    String sparql = template.sparql().replace("{entity}", entityValue);
                    return new MatchResult(sparql, template.predicate(), entityValue);
                }
            }
        }

        return null; // 没有匹配的模板
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
