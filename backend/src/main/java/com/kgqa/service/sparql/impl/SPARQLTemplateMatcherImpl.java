package com.kgqa.service.sparql.impl;

import com.kgqa.kg.MedicalOntology;
import com.kgqa.service.qa.MedicalEntityExtractor;
import com.kgqa.service.sparql.SPARQLTemplateMatcher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * kgdrug SPARQL 模板匹配器。
 */
@Service
public class SPARQLTemplateMatcherImpl implements SPARQLTemplateMatcher {

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
    private static final String DRUG_USAGE = MedicalOntology.DRUG_USAGE.getURI();
    private static final String APPROVAL_NUMBER = MedicalOntology.APPROVAL_NUMBER.getURI();

    private final List<SPARQLTemplate> templates = new ArrayList<>();

    public SPARQLTemplateMatcherImpl() {
        initTemplates();
    }

    private void initTemplates() {
        templates.add(new SPARQLTemplate(
                "(.+)的?症状",
                "SELECT ?answerLabel WHERE { ?disease <" + DISEASE_NAME + "> \"{entityLabel}\" . ?disease <" + HAS_SYMPTOM + "> ?symptom . ?symptom <" + SYMPTOM_NAME + "> ?answerLabel } LIMIT 30",
                "疾病",
                "症状"
        ));
        templates.add(new SPARQLTemplate(
                "哪些疾病(?:会)?(?:出现|有)(.+?)症状",
                "SELECT ?diseaseLabel WHERE { ?symptom <" + SYMPTOM_NAME + "> \"{entityLabel}\" . ?disease <" + HAS_SYMPTOM + "> ?symptom . ?disease <" + DISEASE_NAME + "> ?diseaseLabel } LIMIT 30",
                "症状",
                "症状"
        ));
        templates.add(new SPARQLTemplate(
                "(.+)用什么药",
                "SELECT ?drugLabel WHERE { ?disease <" + DISEASE_NAME + "> \"{entityLabel}\" . ?disease <" + NEED_CURE + "> ?drug . ?drug <" + DRUG_NAME + "> ?drugLabel } LIMIT 30",
                "疾病",
                "用药"
        ));
        templates.add(new SPARQLTemplate(
                "(?:哪些?|什么)药(?:物)?(?:可以)?治疗(.+)",
                "SELECT ?drugLabel WHERE { ?disease <" + DISEASE_NAME + "> \"{entityLabel}\" . ?disease <" + NEED_CURE + "> ?drug . ?drug <" + DRUG_NAME + "> ?drugLabel } LIMIT 30",
                "疾病",
                "用药"
        ));
        templates.add(new SPARQLTemplate(
                "(.+)的?治疗(?:方法|方案)?",
                "SELECT ?answerLabel WHERE { ?disease <" + DISEASE_NAME + "> \"{entityLabel}\" . ?disease <" + TREATMENT + "> ?answerLabel } LIMIT 10",
                "疾病",
                "治疗"
        ));
        templates.add(new SPARQLTemplate(
                "(.+)的?适应症",
                "SELECT ?diseaseLabel WHERE { ?drug <" + DRUG_NAME + "> \"{entityLabel}\" . ?drug <" + CURE + "> ?disease . ?disease <" + DISEASE_NAME + "> ?diseaseLabel } LIMIT 30",
                "药物",
                "适应症"
        ));
        templates.add(new SPARQLTemplate(
                "(.+)的?病因",
                "SELECT ?answerLabel WHERE { ?disease <" + DISEASE_NAME + "> \"{entityLabel}\" . ?disease <" + CAUSE + "> ?answerLabel } LIMIT 10",
                "疾病",
                "病因"
        ));
        templates.add(new SPARQLTemplate(
                "(.+)的?并发症",
                "SELECT ?answerLabel WHERE { ?disease <" + DISEASE_NAME + "> \"{entityLabel}\" . ?disease <" + COMPLICATION + "> ?answerLabel } LIMIT 10",
                "疾病",
                "并发症"
        ));
        templates.add(new SPARQLTemplate(
                "(.+)的?(?:概述|介绍|简介)",
                "SELECT ?answerLabel WHERE { ?disease <" + DISEASE_NAME + "> \"{entityLabel}\" . ?disease <" + OVERVIEW + "> ?answerLabel } LIMIT 5",
                "疾病",
                "概述"
        ));
        templates.add(new SPARQLTemplate(
                "(.+)的?预防(?:方法)?",
                "SELECT ?answerLabel WHERE { ?disease <" + DISEASE_NAME + "> \"{entityLabel}\" . ?disease <" + PREVENTION + "> ?answerLabel } LIMIT 10",
                "疾病",
                "预防"
        ));
        templates.add(new SPARQLTemplate(
                "(.+)的?(?:用法|用量|用药说明)",
                "SELECT ?answerLabel WHERE { ?drug <" + DRUG_NAME + "> \"{entityLabel}\" . ?drug <" + DRUG_USAGE + "> ?answerLabel } LIMIT 10",
                "药物",
                "用法"
        ));
        templates.add(new SPARQLTemplate(
                "(.+)的?(?:批准文号|批号)",
                "SELECT ?answerLabel WHERE { ?drug <" + DRUG_NAME + "> \"{entityLabel}\" . ?drug <" + APPROVAL_NUMBER + "> ?answerLabel } LIMIT 10",
                "药物",
                "批准文号"
        ));
    }

    @Override
    public MatchResult match(String question, MedicalEntityExtractor.ExtractionResult entities) {
        if (question == null || question.isBlank()) {
            return null;
        }

        for (SPARQLTemplate template : templates) {
            Matcher matcher = Pattern.compile(template.pattern()).matcher(question);
            if (!matcher.find()) {
                continue;
            }

            String entityValue = extractEntityValue(matcher, entities, template.entityType());
            if (entityValue == null || entityValue.isBlank()) {
                continue;
            }

            String cleanEntity = cleanEntity(entityValue);
            String sparql = template.sparql().replace("{entityLabel}", escapeLiteral(cleanEntity));
            return new MatchResult(sparql, template.predicate(), cleanEntity);
        }

        return null;
    }

    private String extractEntityValue(Matcher matcher,
                                      MedicalEntityExtractor.ExtractionResult entities,
                                      String entityType) {
        if (matcher.groupCount() >= 1) {
            String extracted = cleanEntity(matcher.group(1));
            if (!extracted.isBlank()) {
                return extracted;
            }
        }

        if (entities != null && entities.entities() != null) {
            for (MedicalEntityExtractor.MedicalEntity entity : entities.entities()) {
                if (entityType.equals(entity.type())) {
                    return entity.value();
                }
            }
        }

        return null;
    }

    private String cleanEntity(String value) {
        return value == null ? "" : value.trim().replaceAll("的$", "");
    }

    private String escapeLiteral(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
