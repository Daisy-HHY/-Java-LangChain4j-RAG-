package com.kgqa.service.rag;

import com.kgqa.model.dto.SourceItem;
import java.util.List;

/**
 * 向量存储管理服务接口
 * 负责向量存储的添加和搜索
 */
public interface VectorStoreManager {

    /**
     * 搜索当前用户可访问的相似文档（无阈值过滤）。
     * userId 为 null 时只允许检索共享文档。
     */
    List<String> search(String query, Long userId, int topK);

    /**
     * 搜索当前用户可访问的相似文档（带阈值过滤，返回分数）。
     * 私有文档按 userId 隔离，同时允许检索共享文档。
     */
    List<SourceItem> searchWithScore(String query, Long userId, int topK, double minScore);

    /**
     * 添加私有文档到向量存储。knowledgeId 和 userId 必须是有效 ID。
     * 缺失用户归属时拒绝写入，不能自动公开文档。
     */
    int addDocuments(List<String> chunks, Long knowledgeId, Long userId);

    /**
     * 显式添加经过授权的共享文档，仅供系统/管理员导入使用。
     */
    int addSharedDocuments(List<String> chunks, Long knowledgeId);

    /**
     * 重建私有知识库：先完成全部向量化，再删除旧向量并写入新向量。
     * 向量数据库替换不是原子操作，运行前应备份并暂停并发写入。
     */
    int replaceDocuments(List<String> chunks, Long knowledgeId, Long userId);

    /**
     * 根据知识库ID删除向量
     * @param knowledgeId 知识库ID
     */
    void deleteByKnowledgeId(Long knowledgeId);
}
