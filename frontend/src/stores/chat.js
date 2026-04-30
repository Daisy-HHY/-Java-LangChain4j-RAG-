import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chat, getSessions, deleteSession, getChatHistory } from '@/api/qa'

export const useChatStore = defineStore('chat', () => {
  // State
  const sessions = ref([])
  const currentSessionId = ref(null)
  const messages = ref([])
  const isLoading = ref(false)
  const error = ref(null)
  const inputValue = ref('')

  // Getters
  const currentSession = computed(() =>
    sessions.value.find(s => s.sessionId === currentSessionId.value)
  )

  const hasMessages = computed(() => messages.value.length > 0)

  // Actions
  async function loadSessions() {
    try {
      const data = await getSessions()
      sessions.value = sortSessions(data)
    } catch (e) {
      error.value = '加载会话列表失败'
      console.error(e)
    }
  }

  function sortSessions(data) {
    return [...(data || [])].sort((a, b) => sessionTime(b) - sessionTime(a))
  }

  function sessionTime(session) {
    return parseTime(session?.updatedAt || session?.createdAt)
  }

  function parseTime(value) {
    if (!value) return 0
    if (Array.isArray(value)) {
      const [year, month, day, hour = 0, minute = 0, second = 0] = value
      return new Date(year, month - 1, day, hour, minute, second).getTime()
    }
    const timestamp = new Date(value).getTime()
    return Number.isFinite(timestamp) ? timestamp : 0
  }

  async function sendMessage(question) {
    if (!question.trim()) return

    isLoading.value = true
    error.value = null

    // 添加用户消息
    messages.value.push({
      role: 'USER',
      content: question,
      timestamp: new Date().toISOString()
    })

    try {
      const response = await chat(question, currentSessionId.value)

      // 更新会话ID
      if (response.sessionId && !currentSessionId.value) {
        currentSessionId.value = response.sessionId
      }

      // 添加助手消息
      messages.value.push({
        role: 'ASSISTANT',
        content: response.answer,
        sources: response.sources || [],
        timestamp: new Date().toISOString()
      })

      // 清空输入
      inputValue.value = ''

      // 重新加载会话列表
      await loadSessions()

      return response
    } catch (e) {
      error.value = '发送消息失败'
      messages.value.push({
        role: 'ERROR',
        content: '抱歉，发生了错误，请稍后重试。',
        timestamp: new Date().toISOString()
      })
      console.error(e)
    } finally {
      isLoading.value = false
    }
  }

  async function loadHistory(sessionId) {
    if (!sessionId) return

    try {
      const history = await getChatHistory(sessionId)
      currentSessionId.value = sessionId
      messages.value = history.map(msg => ({
        role: msg.role,
        content: msg.content,
        sources: msg.sources ? JSON.parse(msg.sources) : [],
        timestamp: msg.createdAt
      }))
    } catch (e) {
      error.value = '加载历史记录失败'
      console.error(e)
    }
  }

  async function removeSession(sessionId) {
    try {
      await deleteSession(sessionId)
      sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)

      if (currentSessionId.value === sessionId) {
        currentSessionId.value = null
        messages.value = []
      }
    } catch (e) {
      error.value = '删除会话失败'
      console.error(e)
    }
  }

  function clearCurrentMessages() {
    messages.value = []
    currentSessionId.value = null
  }

  return {
    // State
    sessions,
    currentSessionId,
    messages,
    isLoading,
    error,
    inputValue,
    // Getters
    currentSession,
    hasMessages,
    // Actions
    loadSessions,
    sendMessage,
    loadHistory,
    removeSession,
    clearCurrentMessages
  }
})
