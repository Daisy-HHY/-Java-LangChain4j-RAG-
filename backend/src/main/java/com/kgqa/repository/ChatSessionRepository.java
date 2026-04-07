package com.kgqa.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kgqa.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionRepository extends BaseMapper<ChatSession> {
}
