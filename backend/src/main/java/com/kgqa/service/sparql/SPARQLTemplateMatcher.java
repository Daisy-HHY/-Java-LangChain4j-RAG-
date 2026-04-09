package com.kgqa.service.sparql;

import com.kgqa.service.qa.MedicalEntityExtractor;

/**
 * SPARQL 模板匹配器接口
 * 根据问题模板匹配生成 SPARQL 查询
 */
public interface SPARQLTemplateMatcher {

    /**
     * 匹配模板并生成 SPARQL
     * @param question 用户问题
     * @param entities 实体抽取结果
     * @return 匹配结果，如果没有匹配返回 null
     */
    MatchResult match(String question, MedicalEntityExtractor.ExtractionResult entities);

    /**
     * SPARQL 模板
     */
    record SPARQLTemplate(String pattern, String sparql, String entityType, String predicate) {}

    /**
     * 匹配结果
     */
    record MatchResult(String sparql, String predicate, String entityValue) {}
}
