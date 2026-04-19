package com.kgqa.init;

import com.kgqa.data.KgdrugDataImporter;
import com.kgqa.kg.TdbManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器
 * TDB2 知识图谱已预加载 kgdrug 数据
 * 此处仅初始化 RAG 向量库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final KgdrugDataImporter kgdrugDataImporter;
    private final TdbManager tdbManager;

    @Override
    public void run(ApplicationArguments args) {
        log.info("===== 开始数据初始化 =====");

        // RAG 向量库使用 kgdrug 数据
        log.info("开始导入 kgdrug RAG 数据到 pgvector...");
        int count = kgdrugDataImporter.importAll();
        log.info("kgdrug RAG 数据导入完成，共 {} 条", count);

        log.info("===== 数据初始化完成 =====");
    }
}
