package com.kgqa.service.rag;

import dev.langchain4j.model.chat.ChatModel;

import java.util.List;

/**
 * RAG 管道服务接口
 * 负责向量检索和 LLM 生成答案
 */
public interface RAGPipeline {

    /**
     * 获取 ChatModel
     * @return ChatModel 实例
     */
    ChatModel getChatModel();

    /**
     * 执行 RAG 问答
     * @param question 问题
     * @param chatHistory 对话历史
     * @return 问答结果
     */
    Result answer(String question, List<String> chatHistory);

    /**
     * 问答结果
     */
    record Result(String answer, List<String> sources) {}
}
