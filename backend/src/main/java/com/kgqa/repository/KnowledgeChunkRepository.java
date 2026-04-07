package com.kgqa.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kgqa.model.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeChunkRepository extends BaseMapper<KnowledgeChunk> {
}
