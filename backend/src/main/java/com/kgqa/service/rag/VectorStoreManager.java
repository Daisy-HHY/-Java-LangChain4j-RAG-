package com.kgqa.service.rag;

import java.util.List;

/**
 * 向量存储管理服务接口
 * 负责向量存储的添加和搜索
 */
public interface VectorStoreManager {

    /**
     * 搜索相似文档
     * @param query 查询文本
     * @param topK 返回数量
     * @return 匹配的文档列表
     */
    List<String> search(String query, int topK);

    /**
     * 添加文档到向量存储
     * @param chunks 文档列表
     * @return 添加的数量
     */
    int addDocuments(List<String> chunks);
}
