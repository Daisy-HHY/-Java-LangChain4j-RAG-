import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatStream, getSessions, deleteSession, getChatHistory } from '@/api/qa'

export const useChatStore = defineStore('chat', () => {
  // State
  const sessions = ref([])
  const currentSessionId = ref(null)
  const messages = ref([])
  const isLoading = ref(false)
  const isStreaming = ref(false)
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

  function createStreamRenderer(message) {
    const streamIntervalMs = 35
    let pending = ''
    let timer = null
    let drainResolver = null

    function ensureTimer() {
      if (!timer) {
        timer = window.setInterval(flush, streamIntervalMs)
      }
    }

    function resolveDrainIfIdle() {
      if (!pending && drainResolver) {
        const resolve = drainResolver
        drainResolver = null
        resolve()
      }
    }

    function flush() {
      if (!pending) {
        window.clearInterval(timer)
        timer = null
        resolveDrainIfIdle()
        return
      }

      const chars = Array.from(pending)
      const take = chars.length > 120 ? 3 : chars.length > 60 ? 2 : 1
      message.content += chars.slice(0, take).join('')
      pending = chars.slice(take).join('')
    }

    return {
      push(text) {
        if (!text) return
        pending += text
        ensureTimer()
      },
      drain() {
        if (!pending && !timer) {
          return Promise.resolve()
        }
        ensureTimer()
        return new Promise(resolve => {
          drainResolver = resolve
        })
      },
      stop() {
        if (timer) {
          window.clearInterval(timer)
          timer = null
        }
        pending = ''
        resolveDrainIfIdle()
      }
    }
  }

  async function sendMessage(question) {
    const submittedQuestion = question.trim()
    if (!submittedQuestion) return

    isLoading.value = true
    isStreaming.value = true
    error.value = null
    inputValue.value = ''

    // 添加用户消息
    messages.value.push({
      role: 'USER',
      content: submittedQuestion,
      timestamp: new Date().toISOString()
    })

    const assistantMessage = {
      role: 'ASSISTANT',
      content: '',
      sources: [],
      timestamp: new Date().toISOString(),
      streaming: true
    }
    messages.value.push(assistantMessage)
    const streamRenderer = createStreamRenderer(assistantMessage)

    try {
      let finalResponse = null

      await chatStream(submittedQuestion, currentSessionId.value, {
        onSession(sessionId) {
          if (sessionId && !currentSessionId.value) {
            currentSessionId.value = sessionId
          }
        },
        onToken(token) {
          streamRenderer.push(token)
        },
        onSources(sources) {
          assistantMessage.sources = sources || []
        },
        onDone(response) {
          finalResponse = response
          assistantMessage.sources = response.sources || assistantMessage.sources || []
          if (response.sessionId && !currentSessionId.value) {
            currentSessionId.value = response.sessionId
          }
        }
      })

      await streamRenderer.drain()
      if (!assistantMessage.content && finalResponse?.answer) {
        assistantMessage.content = finalResponse.answer
      }
      assistantMessage.streaming = false

      // 重新加载会话列表
      await loadSessions()

      return finalResponse
    } catch (e) {
      error.value = '发送消息失败'
      inputValue.value = submittedQuestion
      streamRenderer.stop()
      assistantMessage.streaming = false
      const assistantIndex = messages.value.indexOf(assistantMessage)
      if (assistantIndex >= 0 && !assistantMessage.content) {
        messages.value.splice(assistantIndex, 1)
      }
      messages.value.push({
        role: 'ERROR',
        content: '抱歉，发生了错误，请稍后重试。',
        timestamp: new Date().toISOString()
      })
      console.error(e)
    } finally {
      isLoading.value = false
      isStreaming.value = false
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
    isStreaming,
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
