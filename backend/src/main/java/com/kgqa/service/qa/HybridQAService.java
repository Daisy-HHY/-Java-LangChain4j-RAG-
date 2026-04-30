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
    ChatResponse chat(ChatRequest request, Long userId);

    /**
     * 处理用户问答并流式返回助手输出
     * @param request 聊天请求
     * @param userId 当前用户ID
     * @param handler 流式事件回调
     */
    void streamChat(ChatRequest request, Long userId, StreamHandler handler);

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
    List<ChatSession> getSessions(Long userId);

    /**
     * 删除会话
     * @param sessionId 会话ID
     * @return 是否成功
     */
    boolean deleteSession(String sessionId, Long userId);

    /**
     * 问答结果
     */
    record Result(String answer, List<SourceItem> sources) {}

    interface StreamHandler {
        void onSession(String sessionId);

        void onToken(String token);

        void onSources(List<SourceItem> sources);

        void onComplete(String answer, List<SourceItem> sources, String sessionId);
    }
}
