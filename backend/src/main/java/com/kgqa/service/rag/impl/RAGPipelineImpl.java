package com.kgqa.service.rag.impl;

import com.kgqa.model.dto.SourceItem;
import com.kgqa.model.entity.ChatMessageEntity;
import com.kgqa.service.rag.RAGPipeline;
import com.kgqa.service.rag.VectorStoreManager;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 管道服务实现
 * 负责向量检索和 LLM 生成答案
 */
@Component
public class RAGPipelineImpl implements RAGPipeline {

    private final VectorStoreManager vectorStoreManager;
    private final ChatModel chatLanguageModel;

    private static final double DEFAULT_MIN_SCORE = 0.75;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个医学知识助手，请根据参考资料回答用户问题。

            回答时不要提及"选项"、"A选项"、"B选项"、"错误选项"、"对应选项"等考题相关表述。
            直接以医学知识的形式组织语言，简明扼要地回答问题。

            参考资料：
            %s
            """;

    public RAGPipelineImpl(VectorStoreManager vectorStoreManager,
                            ChatModel chatLanguageModel) {
        this.vectorStoreManager = vectorStoreManager;
        this.chatLanguageModel = chatLanguageModel;
    }

    @Override
    public ChatModel getChatModel() {
        return chatLanguageModel;
    }

    @Override
    public Result answer(String question, List<ChatMessageEntity> chatHistory) {
        // 1. 检索相关知识（带阈值过滤）
        List<SourceItem> relevantDocs = vectorStoreManager.searchWithScore(question, 5, DEFAULT_MIN_SCORE);

        // 如果没有高质量结果，降低阈值再搜索一次
        if (relevantDocs.isEmpty()) {
            relevantDocs = vectorStoreManager.searchWithScore(question, 5, 0.5);
        }

        // 2. 构建上下文（只取文本内容）
        String context = relevantDocs.stream()
                .map(SourceItem::getContent)
                .collect(Collectors.joining("\n---\n"));
        String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, context);

        // 3. 构建带历史的新问题
        String fullQuestion = buildQuestionWithHistory(question, chatHistory);

        // 4. 调用 LLM - SystemPrompt 和 UserMessage 分离
        ChatResponse response = chatLanguageModel.chat(
                SystemMessage.from(systemPrompt),
                UserMessage.from(fullQuestion)
        );

        String answer = response.aiMessage().text();

        return new Result(answer, relevantDocs);
    }

    /**
     * 将对话历史拼接到问题中（使用 role 字段区分用户/助手）
     */
    private String buildQuestionWithHistory(String question, List<ChatMessageEntity> chatHistory) {
        if (chatHistory == null || chatHistory.isEmpty()) {
            return question;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【对话历史】\n");
        for (ChatMessageEntity msg : chatHistory) {
            if ("USER".equals(msg.getRole())) {
                sb.append("用户：").append(msg.getContent()).append("\n");
            } else {
                sb.append("助手：").append(msg.getContent()).append("\n");
            }
        }
        sb.append("【当前问题】").append(question);
        return sb.toString();
    }
}
