package com.kgqa.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.model.entity.KnowledgeChunk;
import com.kgqa.service.rag.VectorStoreManager;
import com.kgqa.repository.KnowledgeChunkRepository;
import com.kgqa.repository.KnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VectorStoreInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreInitializer.class);

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStoreManager vectorStoreManager;

    @Value("${kgqa.rag.reindex-on-startup:false}")
    private boolean reindexOnStartup;

    public VectorStoreInitializer(KnowledgeRepository knowledgeRepository,
                                KnowledgeChunkRepository chunkRepository,
                                VectorStoreManager vectorStoreManager) {
        this.knowledgeRepository = knowledgeRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStoreManager = vectorStoreManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!reindexOnStartup) {
            log.info("跳过启动时向量重建；pgvector 使用已持久化的文档向量");
            return;
        }

        log.info("========== 开始初始化向量存储 ==========");

        try {
            // 查询所有状态为 READY 的知识库
            List<KnowledgeBase> knowledgeList = knowledgeRepository.selectList(
                    new LambdaQueryWrapper<KnowledgeBase>()
                            .eq(KnowledgeBase::getStatus, "READY")
                            .ne(KnowledgeBase::getFileType, "MedQA")
            );

            if (knowledgeList.isEmpty()) {
                log.info("没有找到已就绪的知识库，跳过向量存储初始化");
                return;
            }

            int totalChunks = 0;

            for (KnowledgeBase knowledge : knowledgeList) {
                log.info("正在加载知识库: {} (ID: {})", knowledge.getTitle(), knowledge.getId());

                // 查询该知识库的所有知识块
                List<KnowledgeChunk> chunks = chunkRepository.selectList(
                        new LambdaQueryWrapper<KnowledgeChunk>()
                                .eq(KnowledgeChunk::getKnowledgeId, knowledge.getId())
                                .orderByAsc(KnowledgeChunk::getChunkIndex)
                );

                if (chunks.isEmpty()) {
                    continue;
                }

                // 提取文本内容
                List<String> texts = chunks.stream()
                        .map(KnowledgeChunk::getContent)
                        .toList();

                // 批量添加到向量存储
                int count = vectorStoreManager.addDocuments(texts);
                totalChunks += count;

                log.info("已加载 {} 条知识块 from: {}", count, knowledge.getTitle());
            }

            log.info("========== 向量存储初始化完成，共加载 {} 条知识块 ==========", totalChunks);

        } catch (Exception e) {
            log.error("向量存储初始化失败: {}", e.getMessage(), e);
        }
    }
}
