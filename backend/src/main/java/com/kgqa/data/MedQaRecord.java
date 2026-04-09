package com.kgqa.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;
import java.util.List;

/**
 * MedQA 医学问答数据模型
 */
@Data
public class MedQaRecord {

    @JsonProperty("question")
    private String question;

    @JsonProperty("options")
    private Map<String, String> options;

    @JsonProperty("answer")
    private String answer;

    @JsonProperty("answer_idx")
    private Integer answerIdx;

    @JsonProperty("metamap_phrases")
    private List<String> metamapPhrases;

    @JsonProperty("meta_info")
    private String metaInfo;

    /**
     * 拼接成完整文本，用于向量化
     */
    public String toEmbeddingText() {
        StringBuilder sb = new StringBuilder();
        sb.append("【问题】").append(question).append("\n");

        if (options != null) {
            options.forEach((k, v) -> sb.append("【选项 ").append(k).append("】").append(v).append("\n"));
        }

        sb.append("【答案】").append(answer);
        if (options != null && answer != null) {
            sb.append(" - ").append(options.getOrDefault(answer, ""));
        }
        sb.append("\n");

        if (metaInfo != null && !metaInfo.isEmpty()) {
            sb.append("【知识点】").append(metaInfo).append("\n");
        }

        return sb.toString();
    }

    /**
     * 拼接答案解释文本，用于三元组抽取
     */
    public String toExtractionText() {
        StringBuilder sb = new StringBuilder();
        sb.append("问题：").append(question).append("\n");

        if (options != null && answer != null) {
            sb.append("正确答案：").append(answer).append(" - ").append(options.getOrDefault(answer, "")).append("\n");
        }

        if (metamapPhrases != null && !metamapPhrases.isEmpty()) {
            sb.append("医学概念：").append(String.join(", ", metamapPhrases)).append("\n");
        }

        if (metaInfo != null && !metaInfo.isEmpty()) {
            sb.append("知识点分类：").append(metaInfo);
        }

        return sb.toString();
    }
}
