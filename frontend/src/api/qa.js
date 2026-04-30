import apiClient, { API_BASE_URL } from './index'

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
 * 流式发送问答消息
 * @param {string} question - 问题内容
 * @param {string} sessionId - 会话ID（可选）
 * @param {Object} handlers - 流式事件回调
 */
export async function chatStream(question, sessionId, handlers = {}) {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), 180000)

  try {
    const response = await fetch(`${API_BASE_URL}/qa/chat/stream`, {
      method: 'POST',
      credentials: 'include',
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({
        question,
        sessionId: sessionId || null
      })
    })

    if (response.status === 401 && window.location.pathname !== '/login') {
      localStorage.removeItem('kgqa_auth_user')
      localStorage.removeItem('kgqa_auth_expires_at')
      window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
      throw new Error('未登录或登录已过期')
    }

    if (!response.ok || !response.body) {
      throw new Error(`流式请求失败：${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() || ''

      events.forEach(rawEvent => dispatchStreamEvent(rawEvent, handlers))
    }

    if (buffer.trim()) {
      dispatchStreamEvent(buffer, handlers)
    }
  } finally {
    window.clearTimeout(timeoutId)
  }
}

function dispatchStreamEvent(rawEvent, handlers) {
  const lines = rawEvent.split(/\r?\n/)
  let eventName = 'message'
  const dataLines = []

  lines.forEach(line => {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  })

  if (!dataLines.length) return

  const dataText = dataLines.join('\n')
  const payload = JSON.parse(dataText)

  if (eventName === 'session') handlers.onSession?.(payload.sessionId)
  if (eventName === 'token') handlers.onToken?.(payload.content || '')
  if (eventName === 'sources') handlers.onSources?.(payload.sources || [])
  if (eventName === 'done') handlers.onDone?.(payload)
  if (eventName === 'error') {
    handlers.onError?.(payload)
    throw new Error(payload.message || '流式回答失败')
  }
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
