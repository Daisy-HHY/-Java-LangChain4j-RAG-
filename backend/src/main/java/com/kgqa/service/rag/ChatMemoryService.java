package com.kgqa.service.rag;

import com.kgqa.model.entity.ChatMessageEntity;
import java.util.List;

/**
 * 对话历史服务接口
 * 管理聊天会话的历史消息
 */
public interface ChatMemoryService {

    /**
     * 获取对话历史
     * @param sessionId 会话ID
     * @return 历史消息列表（保留 role 字段用于区分用户/助手）
     */
    List<ChatMessageEntity> getChatHistory(Long sessionId);

    /**
     * 保存消息
     * @param sessionId 会话ID
     * @param role 角色（USER/ASSISTANT）
     * @param content 消息内容
     * @param sources 来源（可选）
     */
    void saveMessage(Long sessionId, String role, String content, String sources);
}
