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

    /**
     * 用户ID（可选，用于调试和隔离校验）
     */
    private String userId;

    /**
     * 来源类型：DOCUMENT 或 GRAPH
     */
    private String sourceType;

    /**
     * 文档标题（可选）
     */
    private String documentTitle;

    /**
     * 文档块序号（可选）
     */
    private Integer chunkIndex;

    public SourceItem(String content, double score) {
        this.content = content;
        this.score = score;
    }
}
