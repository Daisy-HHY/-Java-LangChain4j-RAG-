package com.kgqa.service.rag.impl;

import com.kgqa.service.rag.RAGPipeline;
import com.kgqa.service.rag.VectorStoreManager;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 管道服务实现
 * 负责向量检索和 LLM 生成答案
 */
@Component
public class RAGPipelineImpl implements RAGPipeline {

    private final VectorStoreManager vectorStoreManager;
    private final ChatModel chatLanguageModel;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的医药知识问答助手。请根据提供的知识库内容回答用户问题。
            如果知识库中没有相关信息，请如实告知用户。
            请用清晰、专业的方式回答问题。

            知识库内容：
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
    public Result answer(String question, List<String> chatHistory) {
        // 1. 检索相关知识
        List<String> relevantDocs = vectorStoreManager.search(question, 5);

        // 2. 构建 prompt
        String context = String.join("\n---\n", relevantDocs);
        String prompt = buildPrompt(question, context, chatHistory);

        // 3. 调用 LLM
        String answer = chatLanguageModel.chat(prompt);

        return new Result(answer, relevantDocs);
    }

    private String buildPrompt(String question, String context, List<String> chatHistory) {
        StringBuilder sb = new StringBuilder();

        // 添加对话历史
        if (chatHistory != null && !chatHistory.isEmpty()) {
            sb.append("对话历史：\n");
            for (int i = 0; i < chatHistory.size(); i += 2) {
                if (i + 1 < chatHistory.size()) {
                    sb.append("用户：").append(chatHistory.get(i)).append("\n");
                    sb.append("助手：").append(chatHistory.get(i + 1)).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append(String.format(SYSTEM_PROMPT, context));
        sb.append("\n用户问题：").append(question);

        return sb.toString();
    }
}
