package com.kgqa.service;

import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.model.entity.KnowledgeChunk;
import com.kgqa.service.rag.TextSplitter;
import com.kgqa.service.rag.VectorStoreManager;
import com.kgqa.repository.KnowledgeChunkRepository;
import com.kgqa.repository.KnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 导入中文医学教科书
 */
@Service
public class TextBookImportService {

    private static final Logger log = LoggerFactory.getLogger(TextBookImportService.class);

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStoreManager vectorStoreManager;
    private final TextSplitter textSplitter;

    public TextBookImportService(KnowledgeRepository knowledgeRepository,
                                  KnowledgeChunkRepository chunkRepository,
                                  VectorStoreManager vectorStoreManager,
                                  TextSplitter textSplitter) {
        this.knowledgeRepository = knowledgeRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStoreManager = vectorStoreManager;
        this.textSplitter = textSplitter;
    }

    /**
     * 导入单本教科书
     */
    @Transactional
    public int importTextBook(String filePath, String title) {
        log.info("开始导入教科书: {}", filePath);

        // 1. 创建知识库记录
        KnowledgeBase knowledge = new KnowledgeBase();
        knowledge.setTitle(title);
        knowledge.setFileName(filePath);
        knowledge.setFileType("TextBook");
        knowledge.setTags("医学教科书");
        knowledge.setStatus("PROCESSING");
        knowledgeRepository.insert(knowledge);

        // 2. 读取文件
        String content;
        try {
            content = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取文件失败: {}", e.getMessage(), e);
            knowledge.setStatus("FAILED");
            knowledgeRepository.updateById(knowledge);
            return 0;
        }

        log.info("读取到 {} 个字符", content.length());

        // 3. 文本分块
        List<String> chunks = textSplitter.split(content);
        log.info("分块后得到 {} 个文本块", chunks.size());

        // 4. 存入向量数据库
        int added = vectorStoreManager.addDocuments(chunks);
        log.info("已添加到向量存储: {} 条", added);

        // 5. 保存到 MySQL
        AtomicInteger totalChunks = new AtomicInteger(0);
        int batchSize = 100;
        List<String> batch = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            batch.add(chunks.get(i));

            if (batch.size() >= batchSize || i == chunks.size() - 1) {
                int chunkIndex = totalChunks.get();
                for (int j = 0; j < batch.size(); j++) {
                    KnowledgeChunk chunk = new KnowledgeChunk();
                    chunk.setKnowledgeId(knowledge.getId());
                    chunk.setContent(batch.get(j));
                    chunk.setChunkIndex(chunkIndex + j);
                    chunkRepository.insert(chunk);
                }
                totalChunks.addAndGet(batch.size());
                batch.clear();
            }
        }

        // 6. 更新状态
        knowledge.setChunkCount(totalChunks.get());
        knowledge.setStatus("READY");
        knowledgeRepository.updateById(knowledge);

        log.info("教科书导入完成: {}，共 {} 条知识块", title, totalChunks.get());
        return totalChunks.get();
    }

    /**
     * 导入多本教科书
     */
    @Transactional
    public int importMultipleBooks(List<BookInfo> books) {
        int total = 0;
        for (BookInfo book : books) {
            int count = importTextBook(book.path(), book.title());
            total += count;
        }
        return total;
    }

    /**
     * 导入所有中文教科书
     */
    @Transactional
    public int importAllChineseTextBooks(String directoryPath) {
        List<BookInfo> books = new ArrayList<>();

        // 常见中文医学教科书
        books.add(new BookInfo(directoryPath + "/内科学.txt", "内科学"));
        books.add(new BookInfo(directoryPath + "/外科学.txt", "外科学"));
        books.add(new BookInfo(directoryPath + "/儿科学.txt", "儿科学"));
        books.add(new BookInfo(directoryPath + "/妇科学.txt", "妇科学"));
        books.add(new BookInfo(directoryPath + "/产科学.txt", "产科学"));
        books.add(new BookInfo(directoryPath + "/药理学.txt", "药理学"));
        books.add(new BookInfo(directoryPath + "/病理学.txt", "病理学"));
        books.add(new BookInfo(directoryPath + "/病理生理学.txt", "病理生理学"));
        books.add(new BookInfo(directoryPath + "/生物化学与分子生物学.txt", "生物化学"));
        books.add(new BookInfo(directoryPath + "/生理学.txt", "生理学"));
        books.add(new BookInfo(directoryPath + "/系统解剖学.txt", "系统解剖学"));
        books.add(new BookInfo(directoryPath + "/局部解剖学.txt", "局部解剖学"));
        books.add(new BookInfo(directoryPath + "/组织学与胚胎学.txt", "组织学与胚胎学"));
        books.add(new BookInfo(directoryPath + "/医学微生物学.txt", "医学微生物学"));
        books.add(new BookInfo(directoryPath + "/医学免疫学.txt", "医学免疫学"));
        books.add(new BookInfo(directoryPath + "/医学遗传学.txt", "医学遗传学"));
        books.add(new BookInfo(directoryPath + "/医学细胞生物学.txt", "医学细胞生物学"));
        books.add(new BookInfo(directoryPath + "/人体寄生虫学.txt", "人体寄生虫学"));
        books.add(new BookInfo(directoryPath + "/传染病学.txt", "传染病学"));
        books.add(new BookInfo(directoryPath + "/神经病学.txt", "神经病学"));
        books.add(new BookInfo(directoryPath + "/精神病学.txt", "精神病学"));
        books.add(new BookInfo(directoryPath + "/诊断学.txt", "诊断学"));
        books.add(new BookInfo(directoryPath + "/医学影像学.txt", "医学影像学"));
        books.add(new BookInfo(directoryPath + "/卫生学.txt", "卫生学"));
        books.add(new BookInfo(directoryPath + "/预防医学.txt", "预防医学"));

        return importMultipleBooks(books);
    }

    /**
     * 导入合并的所有教科书
     */
    @Transactional
    public int importAllBooksCombined(String allBooksPath) {
        return importTextBook(allBooksPath, "医学综合教材");
    }

    /**
     * 教科书信息
     */
    public record BookInfo(String path, String title) {}
}
