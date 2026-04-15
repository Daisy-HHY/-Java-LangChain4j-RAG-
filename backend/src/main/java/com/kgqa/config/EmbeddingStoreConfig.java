package com.kgqa.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStoreConfig.class);

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${kgqa.embedding.table:embeddings}")
    private String embeddingTable;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel) {
        log.info("初始化 PgVectorEmbeddingStore...");
        log.info("数据源: {}", datasourceUrl);
        log.info("向量表: {}", embeddingTable);

        // 从 jdbc URL 解析 host 和 port
        // jdbc:postgresql://localhost:5432/kgqa
        String host = "localhost";
        int port = 5432;
        String database = "kgqa";

        if (datasourceUrl.contains("postgresql://")) {
            String[] parts = datasourceUrl.replace("jdbc:postgresql://", "").split("/");
            if (parts.length > 0) {
                String[] hostPort = parts[0].split(":");
                host = hostPort[0];
                if (hostPort.length > 1) {
                    port = Integer.parseInt(hostPort[1]);
                }
            }
            if (parts.length > 1) {
                database = parts[1].split("\\?")[0];
            }
        }

        log.info("连接信息 - Host: {}, Port: {}, Database: {}", host, port, database);

        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(username)
                .password(password)
                .table(embeddingTable)
                .dimension(embeddingModel.dimension())
                .createTable(true)
                .build();
    }
}
