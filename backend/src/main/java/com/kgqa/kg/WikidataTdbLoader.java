package com.kgqa.kg;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Wikidata TDB2 数据加载器
 * 将 TTL 文件加载到 TDB2 数据集
 */
@Component
public class WikidataTdbLoader {

    private static final Logger log = LoggerFactory.getLogger(WikidataTdbLoader.class);

    private final TdbManager tdbManager;

    @Value("${kgqa.wikidata.path:E:/Github_project/LangChain4j-KGQA/wikidata}")
    private String wikidataPath;

    public WikidataTdbLoader(TdbManager tdbManager) {
        this.tdbManager = tdbManager;
    }

    /**
     * 从 wikidata 目录加载所有 TTL 文件到 TDB2
     */
    public int loadAllTtlFiles() {
        List<String> ttlFiles = List.of(
            "disease.ttl",
            "medication.ttl",
            "disease_symptom.ttl",
            "disease_drug.ttl",
            "disease_cause.ttl",
            "disease_exam.ttl",
            "disease_complication.ttl",
            "infectiousdisease.ttl",
            "disease_specialty.ttl",
            "disease_location.ttl"
        );

        int loadedCount = 0;

        for (String fileName : ttlFiles) {
            Path filePath = Paths.get(wikidataPath, fileName);
            if (filePath.toFile().exists()) {
                boolean success = loadTtlFile(filePath.toString());
                if (success) {
                    loadedCount++;
                }
            } else {
                log.warn("TTL 文件不存在: {}", filePath);
            }
        }

        log.info("共加载 {} 个 TTL 文件到 TDB2", loadedCount);
        return loadedCount;
    }

    /**
     * 加载单个 TTL 文件到 TDB2
     */
    public boolean loadTtlFile(String ttlFilePath) {
        try {
            File file = new File(ttlFilePath);
            log.info("开始加载 TTL 文件: {}", ttlFilePath);

            tdbManager.writeTransaction(model -> {
                // 读取 TTL 文件并合并到 model 中
                Model ttlModel = RDFDataMgr.loadModel(ttlFilePath);
                model.add(ttlModel);
                log.info("已合并 {} 个三元组 from {}", ttlModel.size(), file.getName());
            });

            log.info("成功加载: {}", file.getName());
            return true;
        } catch (Exception e) {
            log.error("加载 TTL 文件失败: {} - {}", ttlFilePath, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查 TDB2 是否已有数据
     */
    public boolean isEmpty() {
        return tdbManager.isEmpty();
    }

    /**
     * 获取三元组总数
     */
    public long getTripleCount() {
        return tdbManager.countTriples();
    }
}
