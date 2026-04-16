<script setup>
import { ref, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  isLoading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['example-click'])

const listRef = ref(null)

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

watch(
  () => props.messages.length,
  () => {
    scrollToBottom()
  }
)
</script>

<template>
  <div class="message-list" ref="listRef">
    <!-- 欢迎消息 -->
    <div v-if="messages.length === 0" class="welcome-message">
      <div class="welcome-icon">·</div>
      <h2>医疗问答</h2>
      <p>基于知识图谱与 RAG 的混合问答系统</p>
      <ul class="example-questions">
        <li
          v-for="(q, i) in ['高血压有什么症状？', '糖尿病用什么药治疗？', '冠心病的病因是什么？']"
          :key="i"
          :style="{ animationDelay: `${i * 100}ms` }"
          @click="emit('example-click', q)"
        >
          {{ q }}
        </li>
      </ul>
    </div>

    <!-- 消息列表 -->
    <div v-else class="messages">
      <MessageBubble
        v-for="(msg, index) in messages"
        :key="index"
        :message="msg"
        :style="{ animationDelay: `${index * 50}ms` }"
      />

      <!-- 加载指示器 -->
      <div v-if="isLoading" class="loading-indicator">
        <div class="loading-dot"></div>
        <span>正在思考</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4);
}

.welcome-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: var(--space-8);
  animation: fadeIn var(--transition-slow) ease;
}

.welcome-icon {
  font-size: 3rem;
  font-weight: 200;
  color: var(--text-primary);
  margin-bottom: var(--space-6);
  line-height: 1;
}

.welcome-message h2 {
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--space-2);
  letter-spacing: -0.02em;
}

.welcome-message p {
  color: var(--text-secondary);
  font-size: 0.875rem;
  margin-bottom: var(--space-8);
}

.example-questions {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
  max-width: 300px;
}

.example-questions li {
  padding: var(--space-3) var(--space-4);
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 0.875rem;
  cursor: pointer;
  transition: all var(--transition-fast);
  opacity: 0;
  animation: fadeIn var(--transition-normal) ease forwards;

  &:hover {
    background-color: var(--bg-hover);
    color: var(--text-primary);
    border-color: var(--border-default);
  }
}

.messages {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) 0;
  color: var(--text-muted);
  font-size: 0.875rem;
}

.loading-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--text-muted);
  animation: bounce 1.4s ease-in-out infinite;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(1);
    opacity: 0.4;
  }
  40% {
    transform: scale(1.2);
    opacity: 1;
  }
}
</style>
