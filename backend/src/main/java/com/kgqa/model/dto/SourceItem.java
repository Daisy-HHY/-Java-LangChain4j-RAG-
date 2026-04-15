package com.kgqa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索来源项
 * 包含文档内容和相似度分数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceItem {
    /**
     * 文档内容
     */
    private String content;

    /**
     * 相似度分数 (0-1)
     */
    private double score;

    /**
     * 知识库ID（可选）
     */
    private String knowledgeId;

    public SourceItem(String content, double score) {
        this.content = content;
        this.score = score;
    }
}
