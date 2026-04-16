<script setup>
import { computed } from 'vue'
import SourceCard from './SourceCard.vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const isUser = computed(() => props.message.role === 'USER')
const isError = computed(() => props.message.role === 'ERROR')

function formatTime(timestamp) {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<template>
  <div
    class="message-bubble"
    :class="{
      'user': isUser,
      'assistant': !isUser && !isError,
      'error': isError
    }"
  >
    <div class="bubble-content">
      <div class="message-avatar">
        <span v-if="isUser">·</span>
        <span v-else-if="isError">!</span>
        <span v-else>·</span>
      </div>

      <div class="message-body">
        <div class="message-text" v-html="message.content"></div>

        <!-- 来源卡片 -->
        <SourceCard
          v-if="message.sources && message.sources.length > 0"
          :sources="message.sources"
        />

        <div class="message-time">{{ formatTime(message.timestamp) }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-bubble {
  display: flex;
  animation: fadeIn var(--transition-normal) ease;

  &.user {
    justify-content: flex-end;

    .bubble-content {
      flex-direction: row-reverse;
    }

    .message-body {
      align-items: flex-end;
    }

    .message-text {
      background-color: var(--bg-primary);
      border: 1px solid var(--border-default);
    }

    .message-avatar {
      background-color: var(--bg-primary);
      border: 1px solid var(--border-default);
    }
  }

  &.assistant {
    justify-content: flex-start;

    .message-text {
      background-color: var(--bg-secondary);
      border: 1px solid var(--border-subtle);
    }

    .message-avatar {
      background-color: var(--bg-secondary);
      border: 1px solid var(--border-subtle);
    }
  }

  &.error {
    justify-content: flex-start;

    .message-text {
      background-color: var(--bg-secondary);
      border: 1px solid var(--border-default);
      color: var(--text-primary);
    }
  }
}

.bubble-content {
  display: flex;
  gap: var(--space-3);
  max-width: 85%;
}

.message-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-muted);
  transition: all var(--transition-fast);
}

.message-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.message-text {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  line-height: 1.6;
  color: var(--text-primary);
  white-space: pre-wrap;
  font-size: 0.9375rem;
  transition: all var(--transition-fast);

  &:hover {
    border-color: var(--border-default);
  }
}

.message-time {
  font-size: 0.75rem;
  color: var(--text-muted);
  padding: 0 var(--space-2);
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
