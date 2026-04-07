package com.kgqa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kgqa.model.entity.ChatMessageEntity;
import com.kgqa.model.entity.ChatSession;
import com.kgqa.repository.ChatMessageMapper;
import com.kgqa.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatHistoryController {

    @Autowired
    private final ChatMessageMapper messageMapper;
    private final ChatSessionRepository sessionRepository;

    public ChatHistoryController(ChatMessageMapper messageMapper,
                                 ChatSessionRepository sessionRepository) {
        this.messageMapper = messageMapper;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping("/history/{sessionId}")
    public List<Map<String, Object>> getHistory(@PathVariable("sessionId") String sessionId) {
        ChatSession session = sessionRepository.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId)
        );

        if (session == null) {
            return List.of();
        }

        List<ChatMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, session.getId())
                        .orderByAsc(ChatMessageEntity::getCreatedAt)
        );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return messages.stream().map(msg -> {
            Map<String, Object> map = new HashMap<>();
            map.put("role", msg.getRole());
            map.put("content", msg.getContent());
            map.put("sources", msg.getSources());
            map.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().format(formatter) : null);
            return map;
        }).toList();
    }

    @DeleteMapping("/history/{sessionId}")
    public boolean clearHistory(@PathVariable("sessionId") String sessionId) {
        ChatSession session = sessionRepository.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId)
        );

        if (session != null) {
            messageMapper.delete(
                    new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, session.getId())
            );
            return true;
        }
        return false;
    }
}
