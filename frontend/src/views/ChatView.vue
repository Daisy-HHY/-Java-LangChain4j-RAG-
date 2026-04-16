<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const isReady = ref(false)
const messagesContainer = ref(null)

onMounted(async () => {
  await chatStore.loadSessions()
  isReady.value = true
})

// 自动滚动到底部
watch(() => chatStore.messages.length, async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
})

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
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"></circle>
            <path d="M12 6v6l4 2"></path>
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
            <svg v-if="msg.role === 'USER'" width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
            </svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
            </svg>
          </div>
          <div class="message-content">
            <div class="message-bubble">
              <div class="message-text" v-html="msg.content"></div>
              <div v-if="msg.sources && msg.sources.length" class="sources">
                <div class="sources-header">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                    <polyline points="14 2 14 8 20 8"></polyline>
                  </svg>
                  来源 ({{ msg.sources.length }})
                </div>
                <div v-for="(s, j) in msg.sources" :key="j" class="source-item">
                  {{ s.content }}
                </div>
              </div>
            </div>
            <div class="message-time">{{ msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString('zh-CN', {hour:'2-digit', minute:'2-digit'}) : '' }}</div>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="chatStore.isLoading" class="message assistant loading-message">
          <div class="message-avatar">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
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
  background: var(--bg-primary);
}

.chat-header {
  padding: var(--space-4);
  text-align: center;
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
}

.chat-header h1 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6) var(--space-4);
}

/* Welcome */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
}

.welcome-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-6);
  color: var(--accent-primary);
  animation: pulse 3s ease-in-out infinite;
}

.welcome h2 {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--space-2);
}

.welcome p {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: var(--space-8);
}

.examples {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  width: 320px;
}

.example-btn {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  text-align: left;
}

.example-icon {
  font-size: 12px;
  color: var(--accent-primary);
  transition: transform var(--transition-fast);
}

.example-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--accent-primary);
}

.example-btn:hover .example-icon {
  transform: scale(1.2);
}

/* Messages */
.messages-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  max-width: 680px;
  margin: 0 auto;
}

.message {
  display: flex;
  gap: var(--space-3);
  opacity: 0;
  animation: messageIn 0.3s ease forwards;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  flex-shrink: 0;
  transition: all var(--transition-fast);
}

.message.user .message-avatar {
  background: var(--accent-primary);
  border-color: var(--accent-primary);
  color: white;
}

.message:hover .message-avatar {
  transform: scale(1.05);
}

.message-content {
  max-width: 75%;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.message-bubble {
  padding: var(--space-4);
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  border-bottom-left-radius: var(--radius-sm);
  font-size: 15px;
  line-height: 1.7;
  color: var(--text-primary);
  transition: all var(--transition-fast);
  box-shadow: var(--shadow-sm);
}

.message.user .message-bubble {
  background: var(--bg-secondary);
  border-bottom-left-radius: var(--radius-xl);
  border-bottom-right-radius: var(--radius-sm);
}

.message:hover .message-bubble {
  box-shadow: var(--shadow-md);
}

.message-text {
  word-break: break-word;
}

.sources {
  margin-top: var(--space-3);
  padding: var(--space-3);
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  font-size: 13px;
}

.sources-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-secondary);
  margin-bottom: var(--space-2);
  font-weight: 500;
}

.source-item {
  color: var(--text-secondary);
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--border-subtle);
}

.source-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.message-time {
  font-size: 11px;
  color: var(--text-muted);
  padding: 0 var(--space-2);
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
  padding: var(--space-4) var(--space-5);
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
  padding: var(--space-4);
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-primary);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  gap: var(--space-3);
  max-width: 680px;
  margin: 0 auto;
  align-items: flex-end;
}

.input-wrapper textarea {
  flex: 1;
  padding: var(--space-4);
  background: var(--bg-input);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  font-size: 15px;
  line-height: 1.5;
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
  border-color: var(--accent-primary);
  box-shadow: 0 0 0 3px rgba(45, 41, 38, 0.08);
}

.send-btn {
  width: 48px;
  height: 48px;
  background: var(--accent-primary);
  color: white;
  border: none;
  border-radius: var(--radius-xl);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.send-btn:hover:not(:disabled) {
  background: var(--accent-secondary);
  transform: scale(1.05);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.95);
}

.send-btn:disabled {
  background: var(--bg-hover);
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
</style>
