<script setup>
import { computed, ref, onMounted, nextTick, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import SourceCard from '@/components/chat/SourceCard.vue'

const chatStore = useChatStore()
const authStore = useAuthStore()
const isReady = ref(false)
const messagesContainer = ref(null)
const userAvatarLabel = computed(() => {
  const label = authStore.user?.displayName || authStore.user?.username || 'U'
  return String(label).trim().slice(0, 1).toUpperCase() || 'U'
})

onMounted(async () => {
  await chatStore.loadSessions()
  isReady.value = true
})

// 自动滚动到底部
async function scrollToBottom() {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(() => chatStore.messages.length, scrollToBottom)
watch(() => chatStore.messages.map(msg => msg.content).join('\u0000'), scrollToBottom)

async function handleSend(question) {
  if (!question.trim() || chatStore.isLoading) return
  await chatStore.sendMessage(question)
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend(chatStore.inputValue)
  }
}

function autoResize(e) {
  const textarea = e.target
  textarea.style.height = 'auto'
  textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px'
}

function normalizeAssistantText(content) {
  return String(content || '')
    .replace(/\r\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\*\*/g, '')
    .replace(/([。；;])\s+(?=\d+[.、]\s*)/g, '$1\n')
    .replace(/([：:])\s+(?=\d+[.、]\s*)/g, '$1\n')
    .replace(/\s+(?=\d+[.、]\s*[\u4e00-\u9fa5A-Za-z])/g, '\n')
    .replace(/[ \t]{2,}/g, ' ')
    .trim()
}

function formatMessageBlocks(content, role) {
  const text = role === 'USER'
    ? String(content || '').trim()
    : normalizeAssistantText(content)

  if (!text) return []

  const lines = text.split('\n').map(line => line.trim()).filter(Boolean)
  const blocks = []
  let listItems = []

  function flushList() {
    if (listItems.length) {
      blocks.push({ type: 'list', items: listItems })
      listItems = []
    }
  }

  lines.forEach((line) => {
    const itemMatch = line.match(/^\d+[.、]\s*(.+)$/)
    if (itemMatch) {
      listItems.push(itemMatch[1].trim())
      return
    }

    flushList()
    blocks.push({ type: 'paragraph', text: line })
  })

  flushList()
  return blocks
}
</script>

<template>
  <div class="chat-page">
    <!-- Header -->
    <div class="chat-header">
      <h1>智能问答</h1>
    </div>

    <!-- Message Area -->
    <div class="chat-messages" ref="messagesContainer" v-if="isReady">
      <!-- Welcome State -->
      <div v-if="chatStore.messages.length === 0" class="welcome">
        <div class="welcome-icon">
          <svg class="welcome-medical-icon" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M12 3.2 5.5 5.8v5.4c0 4.1 2.7 7.8 6.5 9.2 3.8-1.4 6.5-5.1 6.5-9.2V5.8L12 3.2Z"></path>
            <path d="M6.8 12h2.5l1.5-4.3 3.1 8.6 1.5-4.3h1.8"></path>
          </svg>
        </div>
        <h2>医疗问答</h2>
        <p>基于知识图谱与 RAG 的混合问答系统</p>
        <div class="examples">
          <button class="example-btn" @click="handleSend('高血压有什么症状？')">
            <span class="example-icon">◈</span>
            高血压有什么症状？
          </button>
          <button class="example-btn" @click="handleSend('糖尿病用什么药治疗？')">
            <span class="example-icon">◈</span>
            糖尿病用什么药治疗？
          </button>
          <button class="example-btn" @click="handleSend('冠心病的病因是什么？')">
            <span class="example-icon">◈</span>
            冠心病的病因是什么？
          </button>
        </div>
      </div>

      <!-- Messages -->
      <div v-else class="messages-list">
        <div
          v-for="(msg, i) in chatStore.messages"
          :key="i"
          class="message"
          :class="msg.role === 'USER' ? 'user' : 'assistant'"
          :style="{ animationDelay: `${i * 50}ms` }"
        >
          <div class="message-avatar">
            <span v-if="msg.role === 'USER'" class="nickname-avatar">{{ userAvatarLabel }}</span>
            <svg v-else class="medical-shield-icon" width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M12 3.2 5.5 5.8v5.4c0 4.1 2.7 7.8 6.5 9.2 3.8-1.4 6.5-5.1 6.5-9.2V5.8L12 3.2Z"></path>
              <path d="M6.8 12h2.5l1.5-4.3 3.1 8.6 1.5-4.3h1.8"></path>
            </svg>
          </div>
          <div class="message-content">
            <div class="message-bubble">
              <div class="message-text">
                <div v-if="msg.streaming && !msg.content" class="streaming-dots">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                </div>
                <template
                  v-for="(block, blockIndex) in formatMessageBlocks(msg.content, msg.role)"
                  :key="`${i}-${blockIndex}`"
                >
                  <p v-if="block.type === 'paragraph'" class="message-paragraph">
                    {{ block.text }}
                  </p>
                  <ol v-else class="message-list-block">
                    <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
                      {{ item }}
                    </li>
                  </ol>
                </template>
                <span v-if="msg.streaming && msg.content" class="stream-cursor"></span>
              </div>
              <SourceCard
                v-if="msg.sources && msg.sources.length"
                :sources="msg.sources"
              />
            </div>
            <div class="message-time">{{ msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString('zh-CN', {hour:'2-digit', minute:'2-digit'}) : '' }}</div>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="chatStore.isLoading && !chatStore.isStreaming" class="message assistant loading-message">
          <div class="message-avatar">
            <svg class="medical-shield-icon" width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M12 3.2 5.5 5.8v5.4c0 4.1 2.7 7.8 6.5 9.2 3.8-1.4 6.5-5.1 6.5-9.2V5.8L12 3.2Z"></path>
              <path d="M6.8 12h2.5l1.5-4.3 3.1 8.6 1.5-4.3h1.8"></path>
            </svg>
          </div>
          <div class="message-content">
            <div class="message-bubble loading-bubble">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="chat-messages loading">
      <div class="loading-dot"></div>
    </div>

    <!-- Input -->
    <div class="chat-input-area">
      <div class="input-wrapper">
        <textarea
          v-model="chatStore.inputValue"
          placeholder="输入问题，按 Enter 发送..."
          rows="1"
          @keydown="handleKeydown"
          @input="autoResize"
        ></textarea>
        <button
          class="send-btn"
          :disabled="chatStore.isLoading || !chatStore.inputValue.trim()"
          @click="handleSend(chatStore.inputValue)"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"></line>
            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background:
    linear-gradient(180deg, rgba(255, 253, 248, 0.5), rgba(247, 243, 236, 0) 260px),
    var(--bg-primary);
}

.chat-header {
  padding: var(--space-4) var(--space-6) var(--space-2);
  text-align: left;
  border-bottom: 0;
  flex-shrink: 0;
  max-width: var(--chat-content-width);
  width: 100%;
  margin: 0 auto;
}

.chat-header h1 {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4) var(--space-5) var(--space-6);
}

/* Welcome */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  max-width: 680px;
  margin: 0 auto;
  padding-bottom: var(--space-12);
}

.welcome-icon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-2xl);
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-5);
  color: var(--accent-clinical);
  box-shadow: var(--shadow-sm);
}

.welcome-medical-icon {
  width: 48px;
  height: 48px;
  color: var(--accent-medical);
  stroke-linecap: round;
  stroke-linejoin: round;
}

.welcome h2 {
  font-size: 1.55rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--space-2);
  letter-spacing: 0;
}

.welcome p {
  font-size: 0.95rem;
  color: var(--text-secondary);
  margin-bottom: var(--space-6);
}

.examples {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  width: 100%;
}

.example-btn {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-height: 88px;
  padding: var(--space-4);
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  font-size: 0.9rem;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  text-align: left;
  line-height: 1.45;
  box-shadow: var(--shadow-sm);
}

.example-icon {
  font-size: 12px;
  color: var(--accent-clinical);
  transition: transform var(--transition-fast);
  padding-top: 2px;
}

.example-btn:hover {
  background: var(--bg-soft);
  color: var(--text-primary);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--border-strong);
}

.example-btn:hover .example-icon {
  transform: scale(1.2);
}

/* Messages */
.messages-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-7);
  max-width: var(--chat-content-width);
  margin: 0 auto;
  width: 100%;
}

.message {
  display: flex;
  gap: var(--space-4);
  opacity: 0;
  animation: messageIn 0.3s ease forwards;
  align-items: flex-start;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  flex-shrink: 0;
  transition: all var(--transition-fast);
}

.message.user .message-avatar {
  background: var(--accent-brand);
  border-color: var(--accent-primary);
  color: var(--bg-elevated);
}

.message.assistant .message-avatar {
  width: 36px;
  height: 36px;
  color: var(--accent-medical);
  background: transparent;
  border-color: transparent;
  border-radius: 0;
}

.nickname-avatar {
  font-size: 0.82rem;
  font-weight: 700;
  line-height: 1;
}

.medical-shield-icon {
  width: 36px;
  height: 36px;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.message:hover .message-avatar {
  transform: translateY(-1px);
}

.message-content {
  max-width: min(100%, 720px);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.message-bubble {
  padding: 2px 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  font-size: 1rem;
  line-height: 1.78;
  color: var(--text-primary);
  transition: all var(--transition-fast);
  box-shadow: none;
}

.message.user .message-bubble {
  padding: 10px var(--space-4);
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  line-height: 1.6;
}

.message:hover .message-bubble {
  box-shadow: none;
}

.message-text {
  word-break: break-word;
  overflow-wrap: anywhere;
}

.message-paragraph {
  margin: 0 0 0.85em;
}

.message-paragraph:last-child {
  margin-bottom: 0;
}

.message-list-block {
  margin: 0.25em 0 1em 1.35em;
  padding: 0;
}

.message-list-block li {
  padding-left: 0.35em;
  margin-bottom: 0.55em;
}

.message-list-block li::marker {
  color: var(--accent-medical);
  font-weight: 600;
}

.message-time {
  font-size: 0.7rem;
  color: var(--text-muted);
  padding-top: 2px;
}

.message.user .message-time {
  text-align: right;
  align-self: flex-end;
}

/* Loading */
.loading-message .message-avatar {
  animation: pulse 1.5s ease-in-out infinite;
}

.loading-bubble {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-3) 0;
}

.loading-bubble .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: bounce 1.4s ease-in-out infinite;
}

.loading-bubble .dot:nth-child(2) { animation-delay: 0.2s; }
.loading-bubble .dot:nth-child(3) { animation-delay: 0.4s; }

.streaming-dots {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 28px;
}

.streaming-dots .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: bounce 1.4s ease-in-out infinite;
}

.streaming-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.streaming-dots .dot:nth-child(3) { animation-delay: 0.4s; }

.stream-cursor {
  display: inline-block;
  width: 2px;
  height: 1.1em;
  margin-left: 2px;
  vertical-align: -0.15em;
  border-radius: 1px;
  background: var(--accent-medical);
  animation: cursorBlink 0.9s steps(2, start) infinite;
}

.loading .loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  margin: 0 auto;
  animation: bounce 1.4s ease-in-out infinite;
}

/* Input */
.chat-input-area {
  padding: var(--space-3) var(--space-5) var(--space-5);
  border-top: 0;
  background: linear-gradient(180deg, rgba(247, 243, 236, 0), var(--bg-primary) 30%);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  gap: var(--space-2);
  max-width: var(--chat-content-width);
  margin: 0 auto;
  align-items: flex-end;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
  padding: var(--space-2);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.input-wrapper:focus-within {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-lg);
}

.input-wrapper textarea {
  flex: 1;
  min-height: 44px;
  max-height: 150px;
  padding: 11px var(--space-3);
  background: transparent;
  border: 0;
  border-radius: var(--radius-lg);
  font-size: 15px;
  line-height: 1.55;
  color: var(--text-primary);
  resize: none;
  font-family: inherit;
  transition: all var(--transition-fast);
}

.input-wrapper textarea::placeholder {
  color: var(--text-muted);
}

.input-wrapper textarea:focus {
  outline: none;
  box-shadow: none;
}

.send-btn {
  width: 40px;
  height: 40px;
  background: var(--accent-primary);
  color: var(--bg-elevated);
  border: none;
  border-radius: var(--radius-lg);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.send-btn:hover:not(:disabled) {
  background: var(--accent-secondary);
  transform: translateY(-1px);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.95);
}

.send-btn:disabled {
  background: var(--bg-hover);
  color: var(--text-muted);
  cursor: not-allowed;
}

@keyframes messageIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(1); opacity: 0.4; }
  40% { transform: scale(1.2); opacity: 1; }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.05); opacity: 1; }
}

@keyframes cursorBlink {
  0%, 45% { opacity: 1; }
  46%, 100% { opacity: 0; }
}

@media (max-width: 768px) {
  .chat-header {
    padding-inline: var(--space-4);
  }

  .chat-messages {
    padding-inline: var(--space-4);
  }

  .examples {
    grid-template-columns: 1fr;
  }

  .message {
    gap: var(--space-3);
  }

  .message-avatar {
    width: 28px;
    height: 28px;
  }

  .message.assistant .message-avatar {
    width: 32px;
    height: 32px;
  }

  .medical-shield-icon {
    width: 32px;
    height: 32px;
  }

  .message-content {
    max-width: calc(100% - 40px);
  }

  .message.user .message-content {
    max-width: 86%;
  }

  .chat-input-area {
    padding: var(--space-3) var(--space-4) calc(72px + var(--space-3));
  }
}
</style>
