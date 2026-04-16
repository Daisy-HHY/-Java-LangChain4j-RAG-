package com.kgqa.data;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wikidata 数据导入器
 * 解析 TTL 文件，提取中文文本，构建 RAG 上下文
 */
@Component
public class WikidataDataImporter {

    private static final Logger log = LoggerFactory.getLogger(WikidataDataImporter.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${kgqa.wikidata.path:E:/Github_project/LangChain4j-KGQA/wikidata}")
    private String wikidataPath;

    public WikidataDataImporter(EmbeddingModel embeddingModel,
                                EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 从 TTL 文件提取 RAG 文本并向量化
     */
    public int importAll() {
        int totalCount = 0;

        // 提取症状关系
        totalCount += importDiseaseSymptom();
        // 提取药物关系
        totalCount += importDiseaseDrug();
        // 提取病因关系
        totalCount += importDiseaseCause();
        // 提取检查关系
        totalCount += importDiseaseExam();
        // 提取并发症关系
        totalCount += importDiseaseComplication();
        // 提取科室关系
        totalCount += importDiseaseSpecialty();
        // 提取部位关系
        totalCount += importDiseaseLocation();

        log.info("Wikidata RAG 数据导入完成，共 {} 条", totalCount);
        return totalCount;
    }

    /**
     * 导入疾病-科室关系
     */
    private int importDiseaseSpecialty() {
        Path filePath = Paths.get(wikidataPath, "disease_specialty.ttl");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("开始导入 disease_specialty.ttl...");
        Model model = loadModel(filePath.toString());

        // 提取 rdfs:label @zh-hans
        Map<String, String> labels = extractChineseLabels(model);

        // 提取疾病-科室关系
        List<String> texts = new ArrayList<>();
        StmtIterator stmtIterator = model.listStatements(
            null,
            model.createProperty("http://www.wikidata.org/prop/direct/P1995"),
            (RDFNode) null
        );
        while (stmtIterator.hasNext()) {
            Statement stmt = stmtIterator.next();
            String diseaseUri = stmt.getSubject().getURI();
            String specialtyUri = stmt.getObject().asResource().getURI();
            String disease = labels.getOrDefault(diseaseUri, extractLabelFromUri(diseaseUri));
            String specialty = labels.getOrDefault(specialtyUri, extractLabelFromUri(specialtyUri));
            texts.add("疾病 " + disease + " 属于科室 " + specialty);
        }
        stmtIterator.close();

        int count = embedTexts(texts);
        log.info("disease_specialty.ttl 导入完成: {} 条", count);
        return count;
    }

    /**
     * 导入疾病-部位关系
     */
    private int importDiseaseLocation() {
        Path filePath = Paths.get(wikidataPath, "disease_location.ttl");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("开始导入 disease_location.ttl...");
        Model model = loadModel(filePath.toString());

        // 提取 rdfs:label @zh-hans
        Map<String, String> labels = extractChineseLabels(model);

        // 提取疾病-部位关系
        List<String> texts = new ArrayList<>();
        StmtIterator stmtIterator = model.listStatements(
            null,
            model.createProperty("http://www.wikidata.org/prop/direct/P927"),
            (RDFNode) null
        );
        while (stmtIterator.hasNext()) {
            Statement stmt = stmtIterator.next();
            String diseaseUri = stmt.getSubject().getURI();
            String locationUri = stmt.getObject().asResource().getURI();
            String disease = labels.getOrDefault(diseaseUri, extractLabelFromUri(diseaseUri));
            String location = labels.getOrDefault(locationUri, extractLabelFromUri(locationUri));
            texts.add("疾病 " + disease + " 位于 " + location);
        }
        stmtIterator.close();

        int count = embedTexts(texts);
        log.info("disease_location.ttl 导入完成: {} 条", count);
        return count;
    }

    private Model loadModel(String filePath) {
        return RDFDataMgr.loadModel(filePath);
    }

    /**
     * 导入疾病-症状关系
     */
    private int importDiseaseSymptom() {
        Path filePath = Paths.get(wikidataPath, "disease_symptom.ttl");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("开始导入 disease_symptom.ttl...");
        Model model = loadModel(filePath.toString());

        // 提取 rdfs:label @zh
        Map<String, String> labels = extractChineseLabels(model);
        log.info("提取到 {} 个中文标签", labels.size());

        // 提取疾病-症状关系
        List<String> texts = new ArrayList<>();
        StmtIterator stmtIterator = model.listStatements(
            null,
            model.createProperty("http://www.wikidata.org/prop/direct/P780"),
            (RDFNode) null
        );
        while (stmtIterator.hasNext()) {
            Statement stmt = stmtIterator.next();
            String diseaseUri = stmt.getSubject().getURI();
            String symptomUri = stmt.getObject().asResource().getURI();
            String disease = labels.getOrDefault(diseaseUri, extractLabelFromUri(diseaseUri));
            String symptom = labels.getOrDefault(symptomUri, extractLabelFromUri(symptomUri));
            texts.add("疾病 " + disease + " 的症状是 " + symptom);
        }
        stmtIterator.close();

        log.info("生成 {} 条 RAG 文本", texts.size());
        if (!texts.isEmpty()) {
            log.info("示例文本: {}", texts.get(0));
        }

        int count = embedTexts(texts);
        log.info("disease_symptom.ttl 导入完成: {} 条", count);
        return count;
    }

    /**
     * 导入疾病-药物关系
     */
    private int importDiseaseDrug() {
        Path filePath = Paths.get(wikidataPath, "drug_disease.ttl");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("开始导入 drug_disease.ttl...");
        Model model = loadModel(filePath.toString());

        Map<String, String> labels = extractChineseLabels(model);

        List<String> texts = new ArrayList<>();
        StmtIterator stmtIterator = model.listStatements(
            null,
            model.createProperty("http://www.wikidata.org/prop/direct/P2176"),
            (RDFNode) null
        );
        while (stmtIterator.hasNext()) {
            Statement stmt = stmtIterator.next();
            String diseaseUri = stmt.getSubject().getURI();
            String drugUri = stmt.getObject().asResource().getURI();
            String disease = labels.getOrDefault(diseaseUri, extractLabelFromUri(diseaseUri));
            String drug = labels.getOrDefault(drugUri, extractLabelFromUri(drugUri));
            texts.add("药物 " + drug + " 可以治疗疾病 " + disease);
        }
        stmtIterator.close();

        int count = embedTexts(texts);
        log.info("drug_disease.ttl 导入完成: {} 条", count);
        return count;
    }

    /**
     * 导入疾病-病因关系
     */
    private int importDiseaseCause() {
        Path filePath = Paths.get(wikidataPath, "disease_cause.ttl");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("开始导入 disease_cause.ttl...");
        Model model = loadModel(filePath.toString());

        Map<String, String> labels = extractChineseLabels(model);

        List<String> texts = new ArrayList<>();
        StmtIterator stmtIterator = model.listStatements(
            null,
            model.createProperty("http://www.wikidata.org/prop/direct/P828"),
            (RDFNode) null
        );
        while (stmtIterator.hasNext()) {
            Statement stmt = stmtIterator.next();
            String diseaseUri = stmt.getSubject().getURI();
            String causeUri = stmt.getObject().asResource().getURI();
            String disease = labels.getOrDefault(diseaseUri, extractLabelFromUri(diseaseUri));
            String cause = labels.getOrDefault(causeUri, extractLabelFromUri(causeUri));
            texts.add("疾病 " + disease + " 的病因是 " + cause);
        }
        stmtIterator.close();

        int count = embedTexts(texts);
        log.info("disease_cause.ttl 导入完成: {} 条", count);
        return count;
    }

    /**
     * 导入疾病-检查关系
     */
    private int importDiseaseExam() {
        Path filePath = Paths.get(wikidataPath, "disease_exam.ttl");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("开始导入 disease_exam.ttl...");
        Model model = loadModel(filePath.toString());

        Map<String, String> labels = extractChineseLabels(model);

        List<String> texts = new ArrayList<>();
        StmtIterator stmtIterator = model.listStatements(
            null,
            model.createProperty("http://www.wikidata.org/prop/direct/P923"),
            (RDFNode) null
        );
        while (stmtIterator.hasNext()) {
            Statement stmt = stmtIterator.next();
            String diseaseUri = stmt.getSubject().getURI();
            String examUri = stmt.getObject().asResource().getURI();
            String disease = labels.getOrDefault(diseaseUri, extractLabelFromUri(diseaseUri));
            String exam = labels.getOrDefault(examUri, extractLabelFromUri(examUri));
            texts.add("疾病 " + disease + " 需要做的检查是 " + exam);
        }
        stmtIterator.close();

        int count = embedTexts(texts);
        log.info("disease_exam.ttl 导入完成: {} 条", count);
        return count;
    }

    /**
     * 导入疾病-并发症关系
     */
    private int importDiseaseComplication() {
        Path filePath = Paths.get(wikidataPath, "disease_complication.ttl");
        if (!filePath.toFile().exists()) {
            log.warn("文件不存在: {}", filePath);
            return 0;
        }

        log.info("开始导入 disease_complication.ttl...");
        Model model = loadModel(filePath.toString());

        Map<String, String> labels = extractChineseLabels(model);

        List<String> texts = new ArrayList<>();
        StmtIterator stmtIterator = model.listStatements(
            null,
            model.createProperty("http://www.wikidata.org/prop/direct/P1542"),
            (RDFNode) null
        );
        while (stmtIterator.hasNext()) {
            Statement stmt = stmtIterator.next();
            String diseaseUri = stmt.getSubject().getURI();
            String complicationUri = stmt.getObject().asResource().getURI();
            String disease = labels.getOrDefault(diseaseUri, extractLabelFromUri(diseaseUri));
            String complication = labels.getOrDefault(complicationUri, extractLabelFromUri(complicationUri));
            texts.add("疾病 " + disease + " 的并发症是 " + complication);
        }
        stmtIterator.close();

        int count = embedTexts(texts);
        log.info("disease_complication.ttl 导入完成: {} 条", count);
        return count;
    }

    /**
     * 提取中文 label（支持简繁体）
     */
    private Map<String, String> extractChineseLabels(Model model) {
        Map<String, String> labels = new HashMap<>();
        StmtIterator iterator = model.listStatements(
            null,
            RDFS.label,
            (RDFNode) null
        );
        while (iterator.hasNext()) {
            Statement stmt = iterator.next();
            if (stmt.getObject().isLiteral()) {
                String lang = stmt.getObject().asLiteral().getLanguage();
                // 匹配 zh, zh-cn, zh-hans 等中文标签
                if ("zh".equals(lang) || "zh-cn".equals(lang) || "zh-hans".equals(lang)) {
                    labels.put(stmt.getSubject().getURI(), stmt.getObject().asLiteral().getString());
                }
            }
        }
        iterator.close();
        return labels;
    }

    /**
     * 向量化文本并写入 EmbeddingStore
     * 分批处理，每批 50 条，避免内存溢出和 API 限流
     */
    private int embedTexts(List<String> texts) {
        if (texts.isEmpty()) {
            return 0;
        }

        log.info("开始向量化 {} 条文本 (分批处理，每批50条)...", texts.size());

        int batchSize = 50;
        int totalSuccess = 0;

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);

            log.info("处理第 {}-{} 条...", i + 1, end);

            List<TextSegment> segments = new ArrayList<>();
            List<Embedding> embeddings = new ArrayList<>();

            for (String text : batch) {
                try {
                    TextSegment segment = TextSegment.from(text);
                    Embedding embedding = embeddingModel.embed(text).content();
                    segments.add(segment);
                    embeddings.add(embedding);
                } catch (Exception e) {
                    log.error("向量化失败: {}, 错误: {}", text, e.getMessage());
                }
            }

            if (!embeddings.isEmpty()) {
                try {
                    embeddingStore.addAll(embeddings, segments);
                    totalSuccess += embeddings.size();
                    log.info("第 {}-{} 条写入成功，当前总数: {}", i + 1, end, totalSuccess);
                } catch (Exception e) {
                    log.error("写入向量存储失败: {}", e.getMessage());
                }
            }

            // 批次之间添加延迟，避免 API 限流
            if (end < texts.size()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        log.info("向量化完成，共成功 {} 条", totalSuccess);
        return totalSuccess;
    }

    /**
     * 从 URI 提取标签
     */
    private String extractLabelFromUri(String uri) {
        if (uri == null) return "";
        int lastSlash = uri.lastIndexOf('/');
        return lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;
    }
}
