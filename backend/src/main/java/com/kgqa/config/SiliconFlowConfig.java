package com.kgqa.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SiliconFlowConfig {

    @Value("${langchain4j.siliconflow.api-key}")
    private String apiKey;

    @Value("${langchain4j.siliconflow.base-url}")
    private String baseUrl;

    @Value("${langchain4j.siliconflow.embedding-model}")
    private String embeddingModel;

    @Value("${langchain4j.siliconflow.chat-model}")
    private String chatModel;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(embeddingModel)
                .build();
    }

    @Bean
    public ChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(chatModel)
                .temperature(0.7)
                .maxTokens(2048)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(chatModel)
                .temperature(0.7)
                .maxTokens(2048)
                .build();
    }
}
