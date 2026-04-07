package com.kgqa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kgqa.model.dto.ChatRequest;
import com.kgqa.model.dto.ChatResponse;
import com.kgqa.model.entity.ChatMessageEntity;
import com.kgqa.model.entity.ChatSession;
import com.kgqa.rag.RAGPipeline;
import com.kgqa.repository.ChatMessageMapper;
import com.kgqa.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QAService {

    private final RAGPipeline ragPipeline;
    private final ChatSessionRepository sessionRepository;

    @Autowired
    private final ChatMessageMapper messageMapper;
    private final ChatMemoryService chatMemoryService;
    private final ObjectMapper objectMapper;

    public QAService(RAGPipeline ragPipeline,
                    ChatSessionRepository sessionRepository,
                    ChatMessageMapper messageMapper,
                    ChatMemoryService chatMemoryService) {
        this.ragPipeline = ragPipeline;
        this.sessionRepository = sessionRepository;
        this.messageMapper = messageMapper;
        this.chatMemoryService = chatMemoryService;
        this.objectMapper = new ObjectMapper();
    }

    public ChatResponse chat(ChatRequest request) {
        // 获取或创建会话
        Long sessionId = getOrCreateSession(request.getSessionId());

        // 获取对话历史
        List<String> history = chatMemoryService.getChatHistory(sessionId);

        // 执行 RAG 问答
        RAGPipeline.Result result = ragPipeline.answer(request.getQuestion(), history);

        // 保存用户消息
        chatMemoryService.saveMessage(sessionId, "USER", request.getQuestion(), null);

        // 保存助手回复
        try {
            String sourcesJson = objectMapper.writeValueAsString(result.sources());
            chatMemoryService.saveMessage(sessionId, "ASSISTANT", result.answer(), sourcesJson);
        } catch (Exception e) {
            chatMemoryService.saveMessage(sessionId, "ASSISTANT", result.answer(), null);
        }

        return new ChatResponse(result.answer(), result.sources(), sessionRepository.selectById(sessionId).getSessionId());
    }

    private Long getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            // 创建新会话
            ChatSession session = new ChatSession();
            session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
            session.setTitle("新会话");
            sessionRepository.insert(session);
            return session.getId();
        }

        // 查找现有会话
        ChatSession session = sessionRepository.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId)
        );

        if (session == null) {
            session = new ChatSession();
            session.setSessionId(sessionId);
            session.setTitle("会话 " + sessionId.substring(0, Math.min(8, sessionId.length())));
            sessionRepository.insert(session);
        }

        return session.getId();
    }

    public List<ChatSession> getSessions() {
        return sessionRepository.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .orderByDesc(ChatSession::getUpdatedAt)
        );
    }

    public boolean deleteSession(String sessionId) {
        ChatSession session = sessionRepository.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId)
        );
        if (session != null) {
            messageMapper.delete(
                    new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, session.getId())
            );
            sessionRepository.deleteById(session.getId());
            return true;
        }
        return false;
    }
}
