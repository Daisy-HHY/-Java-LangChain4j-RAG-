package com.kgqa.service.rag;

import com.kgqa.model.dto.SourceItem;
import java.util.List;

/**
 * 向量存储管理服务接口
 * 负责向量存储的添加和搜索
 */
public interface VectorStoreManager {

    /**
     * 搜索相似文档（无阈值过滤）
     * @param query 查询文本
     * @param topK 返回数量
     * @return 匹配的文档列表
     */
    List<String> search(String query, int topK);

    /**
     * 搜索相似文档（带阈值过滤，返回分数）
     * @param query 查询文本
     * @param topK 返回数量
     * @param minScore 最低相似度分数阈值
     * @return 带分数的文档列表
     */
    List<SourceItem> searchWithScore(String query, int topK, double minScore);

    /**
     * 搜索当前用户可访问的相似文档（带阈值过滤，返回分数）
     * @param query 查询文本
     * @param topK 返回数量
     * @param minScore 最低相似度分数阈值
     * @param userId 当前用户ID
     * @return 带分数的文档列表
     */
    List<SourceItem> searchWithScore(String query, int topK, double minScore, Long userId);

    /**
     * 添加文档到向量存储
     * @param chunks 文档列表
     * @return 添加的数量
     */
    int addDocuments(List<String> chunks);

    /**
     * 添加文档到向量存储（带知识库ID，用于级联删除）
     * @param chunks 文档列表
     * @param knowledgeId 知识库ID
     * @return 添加的数量
     */
    int addDocuments(List<String> chunks, Long knowledgeId);

    /**
     * 添加文档到向量存储（带知识库ID和用户ID，用于用户隔离检索）
     * @param chunks 文档列表
     * @param knowledgeId 知识库ID
     * @param userId 用户ID
     * @return 添加的数量
     */
    int addDocuments(List<String> chunks, Long knowledgeId, Long userId);

    /**
     * 添加文档到向量存储（带来源元数据，用于用户隔离和来源展示）
     * @param chunks 文档列表
     * @param knowledgeId 知识库ID
     * @param userId 用户ID
     * @param documentTitle 文档标题
     * @return 添加的数量
     */
    int addDocuments(List<String> chunks, Long knowledgeId, Long userId, String documentTitle);

    /**
     * 根据知识库ID删除向量
     * @param knowledgeId 知识库ID
     */
    void deleteByKnowledgeId(Long knowledgeId);

    /**
     * 根据知识库ID和用户ID删除向量
     * @param knowledgeId 知识库ID
     * @param userId 用户ID
     */
    void deleteByKnowledgeId(Long knowledgeId, Long userId);
}
