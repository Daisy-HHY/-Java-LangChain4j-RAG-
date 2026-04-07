package com.kgqa.service;

import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.model.entity.KnowledgeChunk;
import com.kgqa.rag.TextSplitter;
import com.kgqa.rag.VectorStoreManager;
import com.kgqa.repository.KnowledgeChunkRepository;
import com.kgqa.repository.KnowledgeRepository;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.tdb.TDBFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DataImportService {

    private static final Logger log = LoggerFactory.getLogger(DataImportService.class);

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStoreManager vectorStoreManager;
    private final TextSplitter textSplitter;

    public DataImportService(KnowledgeRepository knowledgeRepository,
                            KnowledgeChunkRepository chunkRepository,
                            VectorStoreManager vectorStoreManager,
                            TextSplitter textSplitter) {
        this.knowledgeRepository = knowledgeRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStoreManager = vectorStoreManager;
        this.textSplitter = textSplitter;
    }

    /**
     * 从 TDB 知识库导入数据
     * @param tdbPath TDB 数据库路径
     * @param title 知识库标题
     */
    @Transactional
    public int importFromTDB(String tdbPath, String title) {
        log.info("开始从 TDB 导入数据: {}", tdbPath);

        // 1. 创建知识库记录
        KnowledgeBase knowledge = new KnowledgeBase();
        knowledge.setTitle(title);
        knowledge.setFileName(tdbPath);
        knowledge.setFileType("TDB");
        knowledge.setTags("知识图谱,三元组");
        knowledge.setStatus("PROCESSING");
        knowledgeRepository.insert(knowledge);

        // 2. 读取 TDB 三元组并转换
        List<String> triples = readTriplesFromTDB(tdbPath);
        log.info("读取到 {} 条三元组", triples.size());

        // 3. 分批处理并存储
        int batchSize = 100;
        int totalChunks = 0;

        for (int i = 0; i < triples.size(); i += batchSize) {
            int end = Math.min(i + batchSize, triples.size());
            List<String> batch = triples.subList(i, end);

            // 存入向量数据库
            vectorStoreManager.addDocuments(batch);

            // 保存到 MySQL
            for (int j = 0; j < batch.size(); j++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKnowledgeId(knowledge.getId());
                chunk.setContent(batch.get(j));
                chunk.setChunkIndex(i + j);
                chunkRepository.insert(chunk);
            }

            totalChunks += batch.size();
            log.info("已处理 {}/{} 条三元组", end, triples.size());
        }

        // 4. 更新状态
        knowledge.setChunkCount(totalChunks);
        knowledge.setStatus("READY");
        knowledgeRepository.updateById(knowledge);

        log.info("导入完成，共导入 {} 条知识块", totalChunks);
        return totalChunks;
    }

    /**
     * 从 TDB 读取所有三元组并转换为自然语言
     */
    private List<String> readTriplesFromTDB(String tdbPath) {
        List<String> triples = new ArrayList<>();

        try {
            Dataset dataset = TDBFactory.createDataset(tdbPath);
            dataset.begin();

            try {
                String sparql = "SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object } LIMIT 100000";

                try (QueryExecution qexec = QueryExecutionFactory.create(sparql, dataset)) {
                    ResultSet results = qexec.execSelect();

                    while (results.hasNext()) {
                        var row = results.next();
                        String subject = row.get("subject").toString();
                        String predicate = row.get("predicate").toString();
                        String object = row.get("object").toString();

                        // 转换为自然语言格式
                        String tripleText = tripleToNaturalLanguage(subject, predicate, object);
                        if (tripleText != null && !tripleText.isEmpty()) {
                            triples.add(tripleText);
                        }
                    }
                }
            } finally {
                dataset.end();
            }

        } catch (Exception e) {
            log.error("读取 TDB 数据失败: {}", e.getMessage(), e);
        }

        return triples;
    }

    /**
     * 将三元组转换为自然语言
     * 例如:
     *   http://example.org/drug/DB00001 -> 药物: DB00001
     *   http://example.org/name -> 名称
     *   "Aspirin" -> "Aspirin"
     */
    private String tripleToNaturalLanguage(String subject, String predicate, String object) {
        try {
            // 提取简短名称（去掉 URL 前缀）
            String shortSubject = extractShortName(subject);
            String shortPredicate = extractShortName(predicate);
            String shortObject = extractShortName(object);

            // 过滤掉过短的或无意义的triple
            if (shortSubject.length() < 2 || shortPredicate.length() < 2 || shortObject.length() < 1) {
                return null;
            }

            // 格式化为自然语言
            return String.format("%s 的 %s 是 %s", shortSubject, shortPredicate, shortObject);

        } catch (Exception e) {
            log.debug("转换三元组失败: {} - {} - {}", subject, predicate, object);
            return null;
        }
    }

    /**
     * 从 URI 或字符串中提取简短名称
     */
    private String extractShortName(String uriOrString) {
        // 去掉常见前缀
        String result = uriOrString;

        // 处理 URI 格式 (http://...)
        if (result.contains("/")) {
            String[] parts = result.split("/");
            result = parts[parts.length - 1];
        }

        // 处理带 # 的 URI
        if (result.contains("#")) {
            String[] parts = result.split("#");
            result = parts[parts.length - 1];
        }

        // 替换下划线为空格
        result = result.replace("_", " ");

        // 移除引号
        result = result.replace("\"", "").replace("'", "");

        return result.trim();
    }

    /**
     * 导入 CSV 文件
     */
    @Transactional
    public int importFromCSV(String filePath, String title, String tags) {
        log.info("开始从 CSV 导入数据: {}", filePath);

        // 1. 创建知识库记录
        KnowledgeBase knowledge = new KnowledgeBase();
        knowledge.setTitle(title);
        knowledge.setFileName(new File(filePath).getName());
        knowledge.setFileType("CSV");
        knowledge.setTags(tags);
        knowledge.setStatus("PROCESSING");
        knowledgeRepository.insert(knowledge);

        // 2. 读取 CSV
        List<String> records = readCSV(filePath);
        log.info("读取到 {} 条 CSV 记录", records.size());

        // 3. 分批处理并存储
        int batchSize = 100;
        int totalChunks = 0;

        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            List<String> batch = records.subList(i, end);

            // 存入向量数据库
            vectorStoreManager.addDocuments(batch);

            // 保存到 MySQL
            for (int j = 0; j < batch.size(); j++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKnowledgeId(knowledge.getId());
                chunk.setContent(batch.get(j));
                chunk.setChunkIndex(i + j);
                chunkRepository.insert(chunk);
            }

            totalChunks += batch.size();
        }

        // 4. 更新状态
        knowledge.setChunkCount(totalChunks);
        knowledge.setStatus("READY");
        knowledgeRepository.updateById(knowledge);

        log.info("CSV 导入完成，共导入 {} 条知识块", totalChunks);
        return totalChunks;
    }

    /**
     * 读取 CSV 文件
     */
    private List<String> readCSV(String filePath) {
        List<String> records = new ArrayList<>();

        try (var reader = new java.io.BufferedReader(new java.io.FileReader(filePath))) {
            String line;
            String[] headers = null;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // 跳过空行
                if (line.trim().isEmpty()) continue;

                // 解析 CSV
                String[] parts = parseCSVLine(line);

                if (headers == null) {
                    headers = parts;
                    continue; // 跳过表头
                }

                // 将 CSV 行转换为自然语言文本
                String text = csvRowToText(headers, parts);
                if (text != null && !text.isEmpty()) {
                    records.add(text);
                }
            }

        } catch (Exception e) {
            log.error("读取 CSV 文件失败: {}", e.getMessage(), e);
        }

        return records;
    }

    /**
     * 解析 CSV 行（简单处理，实际生产可能需要更复杂的库）
     */
    private String[] parseCSVLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString().trim());

        return parts.toArray(new String[0]);
    }

    /**
     * 将 CSV 行转换为自然语言文本
     */
    private String csvRowToText(String[] headers, String[] values) {
        if (headers.length != values.length || headers.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        // 找到关键字段
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase();
            String value = values[i].trim();

            // 跳过空值
            if (value.isEmpty() || value.equals("null")) continue;

            // 映射常见字段名
            if (header.contains("name") || header.contains("名称")) {
                sb.append("名称: ").append(value);
            } else if (header.contains("id") && !header.contains("accession")) {
                sb.append("ID: ").append(value);
            } else if (header.contains("cas")) {
                sb.append("CAS号: ").append(value);
            } else if (header.contains("synonym") || header.contains("别名")) {
                sb.append("别名: ").append(value);
            } else if (header.contains("indication") || header.contains("适应")) {
                sb.append("适应症: ").append(value);
            } else if (header.contains("description") || header.contains("描述")) {
                sb.append("描述: ").append(value);
            }

            if (sb.length() > 0 && i < headers.length - 1) {
                sb.append("; ");
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }
}
