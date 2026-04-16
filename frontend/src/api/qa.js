import apiClient from './index'

/**
 * 发送问答消息
 * @param {string} question - 问题内容
 * @param {string} sessionId - 会话ID（可选）
 */
export function chat(question, sessionId) {
  return apiClient.post('/qa/chat', {
    question,
    sessionId: sessionId || null
  })
}

/**
 * 获取会话列表
 */
export function getSessions() {
  return apiClient.get('/qa/sessions')
}

/**
 * 删除会话
 * @param {string} sessionId - 会话ID
 */
export function deleteSession(sessionId) {
  return apiClient.delete(`/qa/sessions/${sessionId}`)
}

/**
 * 获取聊天历史
 * @param {string} sessionId - 会话ID
 */
export function getChatHistory(sessionId) {
  return apiClient.get(`/chat/history/${sessionId}`)
}

/**
 * 清空聊天历史
 * @param {string} sessionId - 会话ID
 */
export function clearChatHistory(sessionId) {
  return apiClient.delete(`/chat/history/${sessionId}`)
}
