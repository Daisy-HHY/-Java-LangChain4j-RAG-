package com.kgqa.init;

import com.kgqa.data.KgdrugDataImporter;
import com.kgqa.kg.TdbManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${kgqa.data.import-kgdrug-to-vector-on-startup:false}")
    private boolean importKgdrugToVectorOnStartup;

    @Value("${kgqa.tdb.validate-on-startup:true}")
    private boolean validateTdbOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        log.info("===== 开始数据初始化 =====");

        if (validateTdbOnStartup) {
            long tripleCount = tdbManager.countTriples();
            if (tripleCount == 0) {
                log.warn("TDB 知识图谱当前为空，请检查 kgqa.tdb.path 是否指向已加载的数据集");
            } else {
                log.info("TDB 知识图谱可用，当前三元组数量: {}", tripleCount);
            }
        }

        if (importKgdrugToVectorOnStartup) {
            log.warn("已启用 kgdrug 到 pgvector 的兼容导入。该模式会把知识图谱关系写入 RAG 向量库，仅建议临时迁移使用。");
            int count = kgdrugDataImporter.importAll();
            log.info("kgdrug RAG 兼容导入完成，共 {} 条", count);
        } else {
            log.info("跳过 kgdrug 到 pgvector 的启动导入；RAG 向量库仅由医疗文档上传/切块流程写入");
        }

        log.info("===== 数据初始化完成 =====");
    }
}
