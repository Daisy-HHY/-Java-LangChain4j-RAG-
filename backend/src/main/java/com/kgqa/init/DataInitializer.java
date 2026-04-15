package com.kgqa.init;

import com.kgqa.data.WikidataDataImporter;
import com.kgqa.kg.TdbManager;
import com.kgqa.kg.WikidataTdbLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Wikidata 数据初始化器
 * 1. 加载 TTL 文件到 TDB2
 * 2. 向量化中文文本到 pgvector
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final WikidataTdbLoader wikidataTdbLoader;
    private final WikidataDataImporter wikidataDataImporter;
    private final TdbManager tdbManager;

    @Override
    public void run(ApplicationArguments args) {
        log.info("===== 开始 Wikidata 数据初始化 =====");

        // 1. 加载 TTL 到 TDB2
        if (tdbManager.isEmpty()) {
            log.info("TDB2 为空，开始加载 Wikidata TTL 文件...");
            wikidataTdbLoader.loadAllTtlFiles();
        } else {
            log.info("TDB2 已有 {} 条三元组，跳过加载",
                    tdbManager.countTriples());
        }

        // 2. 向量化 Wikidata 中文文本到 pgvector
        log.info("开始导入 Wikidata RAG 数据到 pgvector...");
        int count = wikidataDataImporter.importAll();
        log.info("Wikidata RAG 数据导入完成，共 {} 条", count);

        log.info("===== Wikidata 数据初始化完成 =====");
    }
}
