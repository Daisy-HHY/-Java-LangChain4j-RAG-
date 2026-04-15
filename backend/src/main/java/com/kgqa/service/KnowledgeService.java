package com.kgqa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kgqa.model.dto.KnowledgeDTO;
import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.model.entity.KnowledgeChunk;
import com.kgqa.service.rag.DocumentLoader;
import com.kgqa.service.rag.TextSplitter;
import com.kgqa.service.rag.VectorStoreManager;
import com.kgqa.repository.KnowledgeChunkRepository;
import com.kgqa.repository.KnowledgeRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final DocumentLoader documentLoader;
    private final TextSplitter textSplitter;
    private final VectorStoreManager vectorStoreManager;

    public KnowledgeService(KnowledgeRepository knowledgeRepository,
                          KnowledgeChunkRepository chunkRepository,
                          DocumentLoader documentLoader,
                          TextSplitter textSplitter,
                          VectorStoreManager vectorStoreManager) {
        this.knowledgeRepository = knowledgeRepository;
        this.chunkRepository = chunkRepository;
        this.documentLoader = documentLoader;
        this.textSplitter = textSplitter;
        this.vectorStoreManager = vectorStoreManager;
    }

    public KnowledgeBase uploadAndProcess(MultipartFile file, String title, String tags) throws Exception {
        // 1. 保存文件元数据
        String originalFilename = file.getOriginalFilename();
        String fileType = file.getContentType();

        KnowledgeBase knowledge = new KnowledgeBase();
        knowledge.setTitle(title);
        knowledge.setFileName(originalFilename);
        knowledge.setFileType(fileType);
        knowledge.setTags(tags);
        knowledge.setStatus("PROCESSING");
        knowledgeRepository.insert(knowledge);

        // 2. 异步处理文档
        processDocumentAsync(knowledge.getId(), file);

        return knowledge;
    }

    @Async
    public void processDocumentAsync(Long knowledgeId, MultipartFile file) {
        try {
            // 解析文档
            String content = documentLoader.loadText(file);

            // 分割文本
            List<String> chunks = textSplitter.split(content);

            // 存入向量数据库（带 knowledge_id 用于级联删除）
            vectorStoreManager.addDocuments(chunks, knowledgeId);

            // 保存 chunk 元数据
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKnowledgeId(knowledgeId);
                chunk.setContent(chunks.get(i));
                chunk.setChunkIndex(i);
                chunkRepository.insert(chunk);
            }

            // 更新状态
            KnowledgeBase knowledge = knowledgeRepository.selectById(knowledgeId);
            knowledge.setChunkCount(chunks.size());
            knowledge.setStatus("READY");
            knowledgeRepository.updateById(knowledge);

        } catch (Exception e) {
            KnowledgeBase knowledge = knowledgeRepository.selectById(knowledgeId);
            if (knowledge != null) {
                knowledge.setStatus("FAILED");
                knowledgeRepository.updateById(knowledge);
            }
        }
    }

    public List<KnowledgeDTO> list(int page, int size) {
        List<KnowledgeBase> records = knowledgeRepository.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .orderByDesc(KnowledgeBase::getCreatedAt)
                        .last("LIMIT " + (page - 1) * size + ", " + size)
        );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return records.stream().map(kb -> {
            KnowledgeDTO dto = new KnowledgeDTO();
            BeanUtils.copyProperties(kb, dto);
            if (kb.getCreatedAt() != null) {
                dto.setCreatedAt(kb.getCreatedAt().format(formatter));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    public boolean delete(Long id) {
        // 级联删除向量数据
        vectorStoreManager.deleteByKnowledgeId(id);
        // 级联删除 MySQL 数据
        chunkRepository.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getKnowledgeId, id));
        knowledgeRepository.deleteById(id);
        return true;
    }

    public String getStatus(Long id) {
        KnowledgeBase knowledge = knowledgeRepository.selectById(id);
        return knowledge != null ? knowledge.getStatus() : null;
    }
}
