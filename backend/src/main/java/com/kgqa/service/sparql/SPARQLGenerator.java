package com.kgqa.service.sparql;

import com.kgqa.service.qa.MedicalEntityExtractor;

/**
 * SPARQL 生成器接口
 * 当模板匹配失败时，使用 LLM 根据问题生成 SPARQL
 */
public interface SPARQLGenerator {

    /**
     * 使用 LLM 生成 SPARQL 查询
     * @param question 用户问题
     * @param entities 实体抽取结果
     * @return 生成的 SPARQL 查询，如果失败返回 null
     */
    String generate(String question, MedicalEntityExtractor.ExtractionResult entities);

    /**
     * 简单的 SPARQL 生成（基于规则）
     * @param question 用户问题
     * @param entities 实体抽取结果
     * @return 生成的 SPARQL 查询，如果失败返回 null
     */
    String generateSimple(String question, MedicalEntityExtractor.ExtractionResult entities);
}
