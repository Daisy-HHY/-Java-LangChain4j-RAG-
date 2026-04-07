package com.kgqa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kgqa.model.entity.ChatMessageEntity;
import com.kgqa.repository.ChatMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatMemoryService {

    @Autowired
    private final ChatMessageMapper messageMapper;

    public ChatMemoryService(ChatMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    public List<String> getChatHistory(Long sessionId) {
        List<ChatMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByAsc(ChatMessageEntity::getCreatedAt)
        );

        List<String> history = new ArrayList<>();
        for (ChatMessageEntity msg : messages) {
            history.add(msg.getContent());
        }
        return history;
    }

    public void saveMessage(Long sessionId, String role, String content, String sources) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSources(sources);
        messageMapper.insert(message);
    }
}
