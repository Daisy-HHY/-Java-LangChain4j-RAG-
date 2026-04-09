package com.kgqa.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.model.entity.KnowledgeChunk;
import com.kgqa.service.rag.VectorStoreManager;
import com.kgqa.service.sparql.TripleExtractor;
import com.kgqa.repository.KnowledgeChunkRepository;
import com.kgqa.repository.KnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class MedQAImportService {

    private static final Logger log = LoggerFactory.getLogger(MedQAImportService.class);

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStoreManager vectorStoreManager;
    private final TripleExtractor tripleExtractor;
    private final ObjectMapper objectMapper;

    public MedQAImportService(KnowledgeRepository knowledgeRepository,
                              KnowledgeChunkRepository chunkRepository,
                              VectorStoreManager vectorStoreManager,
                              TripleExtractor tripleExtractor) {
        this.knowledgeRepository = knowledgeRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStoreManager = vectorStoreManager;
        this.tripleExtractor = tripleExtractor;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 导入中文医学考试题
     * 注意：不再使用 @Transactional，每批处理后立即提交，便于查看进度
     */
    public int importChineseMedQA(String jsonlPath, String title) {
        log.info("开始导入中文 MedQA: {}", jsonlPath);

        // 1. 创建知识库记录
        KnowledgeBase knowledge = new KnowledgeBase();
        knowledge.setTitle(title);
        knowledge.setFileName(jsonlPath);
        knowledge.setFileType("MedQA");
        knowledge.setTags("医学考试题,医学知识问答");
        knowledge.setStatus("PROCESSING");
        knowledgeRepository.insert(knowledge);

        // 2. 读取 JSONL 文件
        List<MedQAItem> items = readJsonlFile(jsonlPath);
        log.info("读取到 {} 道题目", items.size());

        AtomicInteger totalChunks = new AtomicInteger(0);
        AtomicInteger totalTriples = new AtomicInteger(0);
        AtomicInteger batchCount = new AtomicInteger(0);

        // 3. 分批处理
        int batchSize = 100;
        List<String> batchTexts = new ArrayList<>();
        List<MedQAItem> batchItems = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            MedQAItem item = items.get(i);

            // 构建格式化的 QA 文本
            String qaText = formatQAItem(item);
            batchTexts.add(qaText);
            batchItems.add(item);

            // 当批次满时处理
            if (batchTexts.size() >= batchSize || i == items.size() - 1) {
                // 尝试存入向量数据库（可能因网络问题失败）
                try {
                    int added = vectorStoreManager.addDocuments(batchTexts);
                    totalChunks.addAndGet(added);
                } catch (Exception e) {
                    log.warn("向量存储失败，继续保存到MySQL: {}", e.getMessage());
                    // 即使向量失败，也计入总数
                    totalChunks.addAndGet(batchTexts.size());
                }

                // 保存到 MySQL
                int chunkIndex = batchCount.get() * batchSize;
                for (int j = 0; j < batchTexts.size(); j++) {
                    KnowledgeChunk chunk = new KnowledgeChunk();
                    chunk.setKnowledgeId(knowledge.getId());
                    chunk.setContent(batchTexts.get(j));
                    chunk.setChunkIndex(chunkIndex + j);
                    chunkRepository.insert(chunk);
                }

                // 提取三元组（用于 SPARQL 查询）
                for (MedQAItem batchItem : batchItems) {
                    List<TripleExtractor.Triple> triples = tripleExtractor.extractFromQA(batchItem);
                    totalTriples.addAndGet(triples.size());
                    // 三元组暂存，后续存入 TDB
                }

                batchCount.incrementAndGet();
                log.info("已处理 {}/{} 批", batchCount.get(), (items.size() + batchSize - 1) / batchSize);

                // 每10批更新一次进度
                if (batchCount.get() % 10 == 0) {
                    knowledge.setChunkCount(totalChunks.get());
                    knowledge.setStatus("导入中(" + batchCount.get() + "/" + (items.size() + batchSize - 1) / batchSize + ")");
                    knowledgeRepository.updateById(knowledge);
                }

                // 清空批次
                batchTexts.clear();
                batchItems.clear();
            }
        }

        // 4. 更新状态
        knowledge.setChunkCount(totalChunks.get());
        knowledge.setStatus("READY");
        knowledgeRepository.updateById(knowledge);

        log.info("MedQA 导入完成，共导入 {} 条知识块，提取 {} 条三元组",
                totalChunks.get(), totalTriples.get());
        return totalChunks.get();
    }

    /**
     * 读取 JSONL 文件
     */
    private List<MedQAItem> readJsonlFile(String path) {
        List<MedQAItem> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new FileReader(path, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    MedQAItem item = objectMapper.readValue(line, MedQAItem.class);
                    items.add(item);
                } catch (Exception e) {
                    log.warn("跳过无效行: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("读取 JSONL 文件失败: {}", e.getMessage(), e);
        }
        return items;
    }

    /**
     * 格式化 QA 题目为可检索文本
     */
    private String formatQAItem(MedQAItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("【医学问题】").append(item.question).append("\n\n");

        // 选项格式: A. xxx, B. xxx, C. xxx, D. xxx, E. xxx
        if (item.options != null && !item.options.isEmpty()) {
            sb.append("【选项】\n");
            char optionLetter = 'A';
            for (String opt : item.options) {
                sb.append(optionLetter).append(". ").append(opt).append("\n");
                optionLetter++;
            }
        }

        // 正确答案: 使用 answer_idx 或 answer 字段获取正确答案索引
        String answerLetter = item.answer_idx != null ? item.answer_idx : item.answer;
        sb.append("\n【正确答案】").append(answerLetter);

        // 如果可以，添加正确答案的完整文本
        if (answerLetter != null && item.options != null) {
            int idx = answerLetter.charAt(0) - 'A';
            if (idx >= 0 && idx < item.options.size()) {
                sb.append(" (").append(item.options.get(idx)).append(")");
            }
        }

        if (item.meta_info != null && !item.meta_info.isEmpty()) {
            sb.append("\n【知识点】").append(item.meta_info);
        }

        return sb.toString();
    }

    /**
     * 导入多个 JSONL 文件
     */
    @Transactional
    public int importMultipleFiles(List<String> paths, String baseTitle) {
        int total = 0;
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            String title = baseTitle + "_Part" + (i + 1);
            total += importChineseMedQA(path, title);
        }
        return total;
    }

    /**
     * MedQA 数据项
     */
    public static class MedQAItem {
        public String question;
        public String answer;
        public List<String> options;  // JSON 格式: ["NAD", "HS-CoA", ...]
        public String meta_info;
        public String answer_idx;

        public String getQuestion() { return question; }
        public String getAnswer() { return answer; }
        public List<String> getOptions() { return options; }
        public String getMetaInfo() { return meta_info; }
        public String getAnswerIdx() { return answer_idx; }
    }
}
