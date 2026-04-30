package com.kgqa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kgqa.model.dto.KnowledgeDTO;
import com.kgqa.model.entity.KnowledgeBase;
import com.kgqa.model.entity.KnowledgeChunk;
import com.kgqa.service.rag.VectorStoreManager;
import com.kgqa.repository.KnowledgeChunkRepository;
import com.kgqa.repository.KnowledgeRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStoreManager vectorStoreManager;
    private final KnowledgeProcessingService knowledgeProcessingService;

    public KnowledgeService(KnowledgeRepository knowledgeRepository,
                          KnowledgeChunkRepository chunkRepository,
                          VectorStoreManager vectorStoreManager,
                          KnowledgeProcessingService knowledgeProcessingService) {
        this.knowledgeRepository = knowledgeRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStoreManager = vectorStoreManager;
        this.knowledgeProcessingService = knowledgeProcessingService;
    }

    public KnowledgeBase uploadAndProcess(MultipartFile file, String title, String tags, Long userId) throws Exception {
        // 1. 保存文件元数据
        String originalFilename = file.getOriginalFilename();
        String fileType = file.getContentType();
        byte[] fileBytes = file.getBytes();

        KnowledgeBase knowledge = new KnowledgeBase();
        knowledge.setUserId(userId);
        knowledge.setTitle(title);
        knowledge.setFileName(originalFilename);
        knowledge.setFileType(fileType);
        knowledge.setTags(tags);
        knowledge.setChunkCount(0);
        knowledge.setStatus("PROCESSING");
        LocalDateTime now = LocalDateTime.now();
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);
        knowledgeRepository.insert(knowledge);

        // 2. 异步处理文档
        knowledgeProcessingService.processDocumentAsync(knowledge.getId(), fileBytes, fileType);

        return knowledge;
    }

    public List<KnowledgeDTO> list(int page, int size, Long userId) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = (safePage - 1) * safeSize;
        List<KnowledgeBase> records = knowledgeRepository.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, userId)
                        .orderByDesc(KnowledgeBase::getCreatedAt)
                        .last("LIMIT " + safeSize + " OFFSET " + offset)
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

    public boolean delete(Long id, Long userId) {
        KnowledgeBase knowledge = knowledgeRepository.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getId, id)
                        .eq(KnowledgeBase::getUserId, userId)
        );
        if (knowledge == null) {
            return false;
        }
        // 级联删除向量数据
        vectorStoreManager.deleteByKnowledgeId(id);
        // 级联删除 PostgreSQL 元数据
        chunkRepository.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getKnowledgeId, id));
        knowledgeRepository.deleteById(id);
        return true;
    }

    public String getStatus(Long id, Long userId) {
        KnowledgeBase knowledge = knowledgeRepository.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getId, id)
                        .eq(KnowledgeBase::getUserId, userId)
        );
        return knowledge != null ? knowledge.getStatus() : null;
    }
}
