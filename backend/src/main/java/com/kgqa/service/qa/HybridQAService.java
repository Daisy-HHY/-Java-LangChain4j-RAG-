package com.kgqa.service.qa;

import com.kgqa.model.dto.ChatRequest;
import com.kgqa.model.dto.ChatResponse;
import com.kgqa.model.dto.SourceItem;
import com.kgqa.model.entity.ChatMessageEntity;
import com.kgqa.model.entity.ChatSession;

import java.util.List;

/**
 * 混合问答服务接口
 * 结合 SPARQL 知识图谱查询和 RAG 向量检索
 */
public interface HybridQAService {

    /**
     * 处理用户问答
     * @param request 聊天请求
     * @return 聊天响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 执行混合问答
     * @param question 问题
     * @param chatHistory 对话历史（带角色信息）
     * @return 问答结果
     */
    Result answer(String question, List<ChatMessageEntity> chatHistory);

    /**
     * 获取所有会话
     * @return 会话列表
     */
    List<ChatSession> getSessions();

    /**
     * 删除会话
     * @param sessionId 会话ID
     * @return 是否成功
     */
    boolean deleteSession(String sessionId);

    /**
     * 问答结果
     */
    record Result(String answer, List<SourceItem> sources) {}
}
