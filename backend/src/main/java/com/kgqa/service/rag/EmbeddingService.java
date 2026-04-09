package com.kgqa.service.rag;

/**
 * 嵌入服务接口
 * 负责将文本转换为向量
 */
public interface EmbeddingService {

    /**
     * 将文本转换为向量
     * @param text 文本
     * @return 向量数组
     */
    float[] embed(String text);
}
