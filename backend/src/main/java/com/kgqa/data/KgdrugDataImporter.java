package com.kgqa.data;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * kgdrug 数据导入器
 * 从 txt 文件读取疾病、药品、症状名称，构建 RAG 向量库
 */
@Component
public class KgdrugDataImporter {

    private static final Logger log = LoggerFactory.getLogger(KgdrugDataImporter.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${kgqa.kgdrug.dict.path:E:/Github_project/LangChain4j-KGQA/apache_configuration/dirt}")
    private String dictPath;

    @Value("${kgqa.tdb.path:E:/Github_project/LangChain4j-KGQA/apache_configuration/tdb_drug_new}")
    private String tdbPath;

    public KgdrugDataImporter(EmbeddingModel embeddingModel,
                              EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 导入所有 kgdrug 数据到 RAG 向量库
     * 跳过已存在的文本（疾病、症状、药品）
     */
    public int importAll() {
        int totalCount = 0;

        // 1. 跳过疾病名称（已有7775条）
        log.info("跳过疾病名称导入（已有数据）...");

        // 2. 跳过症状名称（已有7770条）
        log.info("跳过症状名称导入（已有数据）...");

        // 3. 跳过药品名称（已有13077条，仅差约37条）
        log.info("跳过药品名称导入（已有数据，仅差约37条）...");

        // 4. 从 TDB 导入疾病-症状关系
        totalCount += importDiseaseSymptomRelations();

        // 5. 从 TDB 导入疾病-药物关系
        totalCount += importDiseaseDrugRelations();

        log.info("kgdrug RAG 数据导入完成，共 {} 条", totalCount);
        return totalCount;
    }

    /**
     * 导入疾病名称
     */
    private int importDiseaseNames() {
        Path filePath = Paths.get(dictPath, "jibing_pos_name.txt");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("导入疾病名称: {}", filePath);
        List<String> texts = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                String disease = line.trim();
                if (!disease.isEmpty() && disease.length() > 1) {
                    // 移除词性标签
                    disease = disease.replaceAll("\\s+(nj|nd|nz)$", "").trim();
                    if (!disease.isEmpty()) {
                        texts.add("疾病: " + disease);
                    }
                }
            }
        } catch (IOException e) {
            log.error("读取文件失败: {}", e.getMessage());
            return 0;
        }

        int count = embedTexts(texts);
        log.info("疾病名称导入完成: {} 条", count);
        return count;
    }

    /**
     * 导入症状名称
     */
    private int importSymptomNames() {
        Path filePath = Paths.get(dictPath, "symptom_pos.txt");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("导入症状名称: {}", filePath);
        List<String> texts = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                String symptom = line.trim();
                if (!symptom.isEmpty() && symptom.length() > 1) {
                    symptom = symptom.replaceAll("\\s+(nj|nd|nz)$", "").trim();
                    if (!symptom.isEmpty()) {
                        texts.add("症状: " + symptom);
                    }
                }
            }
        } catch (IOException e) {
            log.error("读取文件失败: {}", e.getMessage());
            return 0;
        }

        int count = embedTexts(texts);
        log.info("症状名称导入完成: {} 条", count);
        return count;
    }

    /**
     * 导入药品名称
     */
    private int importDrugNames() {
        Path filePath = Paths.get(dictPath, "drug_pos_name.txt");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("导入药品名称: {}", filePath);
        List<String> texts = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                String drug = line.trim();
                if (!drug.isEmpty() && drug.length() > 1) {
                    drug = drug.replaceAll("\\s+(nj|nd|nz)$", "").trim();
                    if (!drug.isEmpty()) {
                        texts.add("药品: " + drug);
                    }
                }
            }
        } catch (IOException e) {
            log.error("读取文件失败: {}", e.getMessage());
            return 0;
        }

        int count = embedTexts(texts);
        log.info("药品名称导入完成: {} 条", count);
        return count;
    }

    /**
     * 从 TDB 导入疾病-症状关系
     */
    private int importDiseaseSymptomRelations() {
        log.info("从 TDB 导入疾病-症状关系...");

        String sparql = """
            SELECT ?disease ?symptomName WHERE {
                ?disease <http://www.kgdrug.com#haszhengzhuang> ?symptom .
                ?symptom <http://www.kgdrug.com#zzname> ?symptomName .
            }
            LIMIT 5000
            """;

        return executeSparqlAndEmbed(sparql, "疾病", "症状");
    }

    /**
     * 从 TDB 导入疾病-药物关系
     */
    private int importDiseaseDrugRelations() {
        log.info("从 TDB 导入疾病-药物关系...");

        String sparql = """
            SELECT ?disease ?drugName WHERE {
                ?disease <http://www.kgdrug.com#needcure> ?drug .
                ?drug <http://www.kgdrug.com#proname> ?drugName .
            }
            LIMIT 5000
            """;

        return executeSparqlAndEmbed(sparql, "疾病", "可用药物");
    }

    /**
     * 执行 SPARQL 查询并生成 RAG 文本
     */
    private int executeSparqlAndEmbed(String sparql, String subjectType, String objectType) {
        List<String> texts = new ArrayList<>();

        try {
            String tdbUrl = "tdb2://localhost/" + tdbPath;
            try (QueryExecution qexec = QueryExecutionFactory.create(sparql, org.apache.jena.query.DatasetFactory.create(tdbPath))) {
                ResultSet results = qexec.execSelect();
                Set<String> seen = new HashSet<>();

                while (results.hasNext()) {
                    org.apache.jena.query.QuerySolution sol = results.next();
                    var diseaseNode = sol.get("disease");
                    var objectNode = subjectType.equals("疾病") ? sol.get("symptomName") : sol.get("drugName");

                    if (diseaseNode != null && objectNode != null) {
                        String diseaseUri = diseaseNode.asResource().getURI();
                        String objectName = objectNode.asLiteral().getString();

                        // 提取疾病名称
                        String diseaseName = extractNameFromUri(diseaseUri);

                        String text = diseaseName + " " + objectType + ": " + objectName;
                        if (!seen.contains(text) && !diseaseName.isEmpty() && !objectName.isEmpty()) {
                            seen.add(text);
                            texts.add(text);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("SPARQL 查询失败: {}", e.getMessage());
            return 0;
        }

        int count = embedTexts(texts);
        log.info("SPARQL 导入完成: {} 条", count);
        return count;
    }

    /**
     * 从 URI 提取名称
     */
    private String extractNameFromUri(String uri) {
        if (uri == null) return "";
        // URI 格式: file:///E:/project_code_2018/KG/kg_drug_new.nt#symptom/337538
        int hashIndex = uri.lastIndexOf('#');
        if (hashIndex > 0) {
            return uri.substring(hashIndex + 1);
        }
        int slashIndex = uri.lastIndexOf('/');
        if (slashIndex > 0) {
            return uri.substring(slashIndex + 1);
        }
        return uri;
    }

    /**
     * 向量化文本并写入 EmbeddingStore
     * 使用 Set 去重，单条插入避免批量冲突
     * 预检查数据库已有文本，跳过重复
     */
    private int embedTexts(List<String> texts) {
        if (texts.isEmpty()) {
            return 0;
        }

        // 去重
        Set<String> uniqueTexts = new HashSet<>(texts);
        List<String> deduplicated = new ArrayList<>(uniqueTexts);

        log.info("开始向量化 {} 条文本（去重后 {} 条）...", texts.size(), deduplicated.size());

        int totalSuccess = 0;
        int skippedExisting = 0;
        int maxRetries = 3;

        for (int i = 0; i < deduplicated.size(); i++) {
            String text = deduplicated.get(i);
            boolean success = false;

            // 先 embed，再 add
            TextSegment segment = null;
            Embedding embedding = null;

            for (int retry = 0; retry < maxRetries; retry++) {
                try {
                    segment = TextSegment.from(text);
                    embedding = embeddingModel.embed(text).content();
                    break;
                } catch (Exception e) {
                    if (retry < maxRetries - 1) {
                        try {
                            Thread.sleep(500 * (retry + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            if (embedding != null) {
                try {
                    embeddingStore.add(embedding, segment);
                    success = true;
                } catch (Exception e) {
                    // 唯一约束冲突 - 跳过（已存在）
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.contains("duplicate key") || msg.contains("unique constraint") || msg.contains("idx_embeddings_text_unique")) {
                        skippedExisting++;
                    } else {
                        log.error("添加失败: {}", text);
                    }
                }
            } else {
                log.error("向量化失败: {}", text);
            }

            if (success) {
                totalSuccess++;
            }

            if ((i + 1) % 50 == 0) {
                log.info("已向量化 {}/{} 条（本次新增: {}，跳过已存在: {}）", i + 1, deduplicated.size(), totalSuccess, skippedExisting);
            }

            // API 请求间隔
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("向量化完成，新增 {} 条，跳过已存在 {} 条", totalSuccess, skippedExisting);
        return totalSuccess;
    }
}
