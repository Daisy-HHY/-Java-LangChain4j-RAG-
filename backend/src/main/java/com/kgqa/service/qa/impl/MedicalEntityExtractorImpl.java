package com.kgqa.service.qa.impl;

import com.kgqa.service.qa.MedicalEntityExtractor;
import com.kgqa.util.MedicalDictionary;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 医学实体抽取服务实现
 * 从用户问题中识别药物、疾病、症状等实体
 */
@Service
public class MedicalEntityExtractorImpl implements MedicalEntityExtractor {

    private final ChatModel chatModel;

    // 提示词
    private static final String EXTRACT_PROMPT = """
            你是一个医学实体识别助手。请从以下用户问题中提取医学实体。

            实体类型：
            - 药物：药物名称，如"阿司匹林"、"布洛芬"、"青霉素"
            - 疾病：疾病名称，如"高血压"、"糖尿病"、"心肌梗死"
            - 症状：症状描述，如"头痛"、"发热"、"胸闷"

            请按以下 JSON 格式返回：
            {
              "entities": [
                {"type": "药物", "value": "实体名称"},
                {"type": "疾病", "value": "实体名称"}
              ],
              "intent": "用户的查询意图，如"查询适应症"、"查询副作用""
            }

            如果没有识别到实体，返回空列表。

            用户问题：%s
            """;

    public MedicalEntityExtractorImpl(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 提取医学实体
     */
    @Override
    public ExtractionResult extract(String question) {
        if (question == null || question.trim().isEmpty()) {
            return new ExtractionResult(List.of(), "未知");
        }

        // 首先使用词典进行快速匹配
        List<MedicalEntity> entities = extractByDictionary(question);

        // 如果词典匹配结果为空，使用 LLM 增强提取
        if (entities.isEmpty()) {
            entities = extractByLLM(question);
        }

        // 推断意图
        String intent = inferIntent(question);

        return new ExtractionResult(entities, intent);
    }

    /**
     * 使用词典快速提取实体
     */
    private List<MedicalEntity> extractByDictionary(String question) {
        List<MedicalEntity> entities = new ArrayList<>();
        Set<String> found = new HashSet<>();

        // 提取药物
        for (String drug : MedicalDictionary.DRUGS) {
            if (question.contains(drug) && !found.contains(drug)) {
                entities.add(new MedicalEntity("药物", drug));
                found.add(drug);
            }
        }

        // 提取疾病
        for (String disease : MedicalDictionary.DISEASES) {
            if (question.contains(disease) && !found.contains(disease)) {
                entities.add(new MedicalEntity("疾病", disease));
                found.add(disease);
            }
        }

        // 提取症状
        for (String symptom : MedicalDictionary.SYMPTOMS) {
            if (question.contains(symptom) && !found.contains(symptom)) {
                entities.add(new MedicalEntity("症状", symptom));
                found.add(symptom);
            }
        }

        return entities;
    }

    /**
     * 使用 LLM 提取实体
     */
    private List<MedicalEntity> extractByLLM(String question) {
        try {
            String prompt = String.format(EXTRACT_PROMPT, question);
            String response = chatModel.chat(prompt).trim();

            // 简单解析 JSON 响应
            List<MedicalEntity> entities = new ArrayList<>();

            // 提取药物
            Pattern drugPattern = Pattern.compile("\"([^\"]+)\".*?药物");
            Matcher drugMatcher = drugPattern.matcher(response);
            while (drugMatcher.find()) {
                entities.add(new MedicalEntity("药物", drugMatcher.group(1)));
            }

            // 提取疾病
            Pattern diseasePattern = Pattern.compile("\"([^\"]+)\".*?疾病");
            Matcher diseaseMatcher = diseasePattern.matcher(response);
            while (diseaseMatcher.find()) {
                entities.add(new MedicalEntity("疾病", diseaseMatcher.group(1)));
            }

            return entities;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 从问题中推断意图
     */
    private String inferIntent(String question) {
        String q = question.toLowerCase();

        if (q.contains("适应症") || q.contains("治疗")) {
            return "查询适应症";
        }
        if (q.contains("副作用") || q.contains("不良反应")) {
            return "查询副作用";
        }
        if (q.contains("禁忌") || q.contains("注意事项")) {
            return "查询禁忌";
        }
        if (q.contains("症状") || q.contains("表现")) {
            return "查询症状";
        }
        if (q.contains("诊断") || q.contains("检查")) {
            return "查询诊断";
        }
        if (q.contains("用法") || q.contains("剂量")) {
            return "查询用法";
        }
        if (q.contains("机制") || q.contains("原理")) {
            return "查询机制";
        }
        if (q.contains("区别") || q.contains("不同")) {
            return "比较";
        }

        return "一般查询";
    }
}
