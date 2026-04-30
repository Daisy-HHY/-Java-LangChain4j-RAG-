package com.kgqa.service.rag;

import com.kgqa.model.dto.SourceItem;
import com.kgqa.model.entity.ChatMessageEntity;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;
import java.util.function.Consumer;

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
     * @param chatHistory 对话历史（带角色信息）
     * @return 问答结果
     */
    Result answer(String question, List<ChatMessageEntity> chatHistory);

    /**
     * 执行 RAG 流式问答
     * @param question 问题
     * @param chatHistory 对话历史（带角色信息）
     * @param tokenConsumer LLM 返回片段回调
     * @return 完整问答结果
     */
    Result answerStreaming(String question, List<ChatMessageEntity> chatHistory, Consumer<String> tokenConsumer);

    /**
     * 问答结果
     * @param answer 回答内容
     * @param sources 来源列表（带分数）
     */
    record Result(String answer, List<SourceItem> sources) {}
}
