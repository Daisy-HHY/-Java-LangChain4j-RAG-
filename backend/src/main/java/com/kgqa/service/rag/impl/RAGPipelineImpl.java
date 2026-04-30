package com.kgqa.service.rag.impl;

import com.kgqa.model.dto.SourceItem;
import com.kgqa.model.entity.ChatMessageEntity;
import com.kgqa.service.rag.RAGPipeline;
import com.kgqa.service.rag.VectorStoreManager;
import com.kgqa.util.AnswerFormatter;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * RAG 管道服务实现
 * 负责向量检索和 LLM 生成答案
 */
@Component
public class RAGPipelineImpl implements RAGPipeline {

    private final VectorStoreManager vectorStoreManager;
    private final ChatModel chatLanguageModel;
    private final StreamingChatModel streamingChatLanguageModel;

    private static final double DEFAULT_MIN_SCORE = 0.75;
    private static final int MAX_HISTORY_MESSAGES = 8;
    private static final int MAX_HISTORY_CHARS = 3000;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个医学知识助手，请根据参考资料回答用户问题。

            你必须**严格基于**以下参考资料回答，不要添加参考资料中没有的信息。
                      如果资料不足，请明确说"资料中未提及"，不要编造。

            回答时不要提及"选项"、"A选项"、"B选项"、"错误选项"、"对应选项"等考题相关表述。

            输出格式要求：
            1. 第一行直接给出简短结论。
            2. 后续按要点分段说明，每段不超过 3 句话。
            3. 涉及多个症状、药品、病因或处理措施时，使用编号列表，每项单独一行。
            4. 不要把所有内容堆在一个段落里。
            5. 不要输出 Markdown 表格。

            参考资料：
            %s
            """;

    public RAGPipelineImpl(VectorStoreManager vectorStoreManager,
                            ChatModel chatLanguageModel,
                            StreamingChatModel streamingChatLanguageModel) {
        this.vectorStoreManager = vectorStoreManager;
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
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

        if (relevantDocs.isEmpty()) {
            return new Result("资料中未提及。当前医疗文档向量库没有检索到可用于回答该问题的内容。", List.of());
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

        return new Result(AnswerFormatter.format(answer), relevantDocs);
    }

    @Override
    public Result answerStreaming(String question, List<ChatMessageEntity> chatHistory, Consumer<String> tokenConsumer) {
        List<SourceItem> relevantDocs = vectorStoreManager.searchWithScore(question, 5, DEFAULT_MIN_SCORE);

        if (relevantDocs.isEmpty()) {
            relevantDocs = vectorStoreManager.searchWithScore(question, 5, 0.5);
        }

        if (relevantDocs.isEmpty()) {
            String answer = "资料中未提及。当前医疗文档向量库没有检索到可用于回答该问题的内容。";
            tokenConsumer.accept(answer);
            return new Result(answer, List.of());
        }

        String context = relevantDocs.stream()
                .map(SourceItem::getContent)
                .collect(Collectors.joining("\n---\n"));
        String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, context);
        String fullQuestion = buildQuestionWithHistory(question, chatHistory);

        StringBuilder answerBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        streamingChatLanguageModel.chat(
                List.of(SystemMessage.from(systemPrompt), UserMessage.from(fullQuestion)),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        if (partialResponse == null || partialResponse.isEmpty()) {
                            return;
                        }
                        answerBuilder.append(partialResponse);
                        tokenConsumer.accept(partialResponse);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        errorRef.set(error);
                        latch.countDown();
                    }
                }
        );

        awaitStreaming(latch, errorRef);
        return new Result(AnswerFormatter.format(answerBuilder.toString()), relevantDocs);
    }

    private void awaitStreaming(CountDownLatch latch, AtomicReference<Throwable> errorRef) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("流式回答被中断", e);
        }

        Throwable error = errorRef.get();
        if (error != null) {
            throw new IllegalStateException("流式回答生成失败", error);
        }
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
        int start = Math.max(0, chatHistory.size() - MAX_HISTORY_MESSAGES);
        int usedChars = 0;
        for (int i = start; i < chatHistory.size(); i++) {
            ChatMessageEntity msg = chatHistory.get(i);
            String content = msg.getContent() == null ? "" : msg.getContent();
            if (usedChars + content.length() > MAX_HISTORY_CHARS) {
                break;
            }
            if ("USER".equals(msg.getRole())) {
                sb.append("用户：").append(content).append("\n");
            } else {
                sb.append("助手：").append(content).append("\n");
            }
            usedChars += content.length();
        }
        sb.append("【当前问题】").append(question);
        return sb.toString();
    }
}
