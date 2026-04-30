package com.kgqa.service.rag.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kgqa.model.entity.ChatMessageEntity;
import com.kgqa.repository.ChatMessageMapper;
import com.kgqa.service.rag.ChatMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史服务实现
 * 管理聊天会话的历史消息
 */
@Service
public class ChatMemoryServiceImpl implements ChatMemoryService {

    @Autowired
    private final ChatMessageMapper messageMapper;

    public ChatMemoryServiceImpl(ChatMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public List<ChatMessageEntity> getChatHistory(Long sessionId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByAsc(ChatMessageEntity::getCreatedAt)
        );
    }

    @Override
    public void saveMessage(Long sessionId, String role, String content, String sources) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSources(sources);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }
}
