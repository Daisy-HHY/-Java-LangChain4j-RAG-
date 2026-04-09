package com.kgqa.init;

import com.kgqa.data.EmbeddingIngestor;
import com.kgqa.data.MedQaParser;
import com.kgqa.kg.KnowledgeGraphBuilder;
import com.kgqa.kg.TdbManager;
import com.kgqa.kg.TripleExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动数据初始化器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final MedQaParser medQaParser;
    private final EmbeddingIngestor embeddingIngestor;
    private final TripleExtractor tripleExtractor;
    private final KnowledgeGraphBuilder kgBuilder;
    private final TdbManager tdbManager;

    @Value("${kgqa.medqa.path:E:/Github_project/LangChain4j-KGQA/data/data_clean/questions/Mainland/chinese_qbank.jsonl}")
    private String medqaPath;

    private static final double MIN_CONFIDENCE = 0.75;
    private static final int BATCH_SIZE = 100;
    private static final boolean SKIP_IF_EXISTS = true;

    @Override
    public void run(ApplicationArguments args) {
        log.info("===== 开始数据初始化 =====");
        log.info("MedQA 数据路径: {}", medqaPath);

        // 检查 TDB 是否已有数据
        if (SKIP_IF_EXISTS && !tdbManager.isEmpty()) {
            log.info("TDB 已有数据（{} 条三元组），跳过 KG 初始化",
                    tdbManager.countTriples());
            return;
        }

        // 分批处理 MedQA
        long startTime = System.currentTimeMillis();
        int[] totalRecords = {0};

        try {
            medQaParser.parseExternalInBatches(medqaPath, BATCH_SIZE, (batch, batchIndex) -> {
                log.info("处理批次 {}，共 {} 条记录", batchIndex, batch.size());
                totalRecords[0] += batch.size();

                // 向量化入库
                try {
                    embeddingIngestor.ingestMedQaBatch(batch, batchIndex);
                } catch (Exception e) {
                    log.error("批次 {} 向量化失败: {}", batchIndex, e.getMessage());
                }

                // 三元组抽取 + 写入 TDB
                try {
                    List<TripleExtractor.Triple> triples =
                            tripleExtractor.extractBatch(batch, MIN_CONFIDENCE);
                    if (!triples.isEmpty()) {
                        kgBuilder.writeTriples(triples);
                    }
                } catch (Exception e) {
                    log.error("批次 {} 三元组抽取失败: {}", batchIndex, e.getMessage());
                }

                if (batchIndex % 10 == 0) {
                    kgBuilder.printStats();
                }
            });
        } catch (Exception e) {
            log.error("MedQA 数据加载失败: {}", e.getMessage());
        }

        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        log.info("===== 数据初始化完成，共处理 {} 条记录，耗时 {}s =====", totalRecords[0], elapsed);
        kgBuilder.printStats();
    }
}
