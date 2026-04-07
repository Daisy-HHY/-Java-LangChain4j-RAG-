package com.kgqa.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kgqa.model.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
