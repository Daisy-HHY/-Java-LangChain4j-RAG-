package com.kgqa.service.qa;

import java.util.List;

/**
 * 医学实体抽取服务接口
 * 从用户问题中识别药物、疾病、症状等实体
 */
public interface MedicalEntityExtractor {

    /**
     * 医学实体
     */
    record MedicalEntity(String type, String value) {}

    /**
     * 抽取结果
     */
    record ExtractionResult(List<MedicalEntity> entities, String intent) {}

    /**
     * 提取医学实体
     * @param question 用户问题
     * @return 抽取结果
     */
    ExtractionResult extract(String question);
}
