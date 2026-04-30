<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import SourceCard from './SourceCard.vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const authStore = useAuthStore()
const isUser = computed(() => props.message.role === 'USER')
const isError = computed(() => props.message.role === 'ERROR')
const formattedBlocks = computed(() => formatAssistantMessage(props.message.content || ''))
const userAvatarLabel = computed(() => {
  const label = authStore.user?.displayName || authStore.user?.username || 'U'
  return String(label).trim().slice(0, 1).toUpperCase() || 'U'
})

function formatTime(timestamp) {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatAssistantMessage(content) {
  if (isUser.value || isError.value) {
    return [{ type: 'paragraph', text: normalizeText(content) }]
  }

  const normalized = normalizeText(content)

  if (!normalized) {
    return []
  }

  const lines = expandInlineNumberedList(normalized)

  const blocks = []
  let listItems = []

  function flushList() {
    if (listItems.length > 0) {
      blocks.push({ type: 'list', items: listItems })
      listItems = []
    }
  }

  for (const line of lines) {
    const item = parseListItem(line)
    if (item) {
      listItems.push(item)
    } else {
      flushList()
      splitLongParagraph(line).forEach(text => {
        blocks.push({ type: 'paragraph', text })
      })
    }
  }

  flushList()
  return blocks
}

function normalizeText(content) {
  return content
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .trim()
}

function expandInlineNumberedList(text) {
  const rawLines = text
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)

  return rawLines.flatMap(line => {
    const inlineItems = splitInlineNumberedItems(line)
    return inlineItems.length > 0 ? inlineItems : splitLongParagraph(line)
  })
}

function splitInlineNumberedItems(line) {
  const markerPattern = /(?:^|[\s，。；：:])(\d{1,2})[\.、\)]\s+/g
  const matches = [...line.matchAll(markerPattern)]

  if (matches.length < 2 && !(matches.length === 1 && matches[0].index > 0)) {
    return []
  }

  const result = []
  const first = matches[0]
  const intro = line.slice(0, first.index).trim()
  if (intro) {
    result.push(intro)
  }

  for (let i = 0; i < matches.length; i++) {
    const match = matches[i]
    const numberOffset = match[0].search(/\d/)
    const markerStart = match.index + Math.max(numberOffset, 0)
    const nextStart = i + 1 < matches.length ? matches[i + 1].index : line.length
    const item = line.slice(markerStart, nextStart).trim()
    if (item) {
      result.push(item)
    }
  }

  return result
}

function parseListItem(line) {
  const numbered = line.match(/^\s*(?:\d+[\.\、\)]|[（(]\d+[）)])\s*(.+)$/)
  if (numbered) return numbered[1].trim()

  const bullet = line.match(/^\s*[-*•]\s+(.+)$/)
  if (bullet) return bullet[1].trim()

  return null
}

function splitLongParagraph(text) {
  if (text.length <= 120) {
    return [text]
  }

  const parts = text
    .split(/(?<=[。！？；;])/)
    .map(part => part.trim())
    .filter(Boolean)

  if (parts.length <= 1) {
    return [text]
  }

  const paragraphs = []
  let current = ''

  for (const part of parts) {
    if ((current + part).length > 110 && current) {
      paragraphs.push(current)
      current = part
    } else {
      current += part
    }
  }

  if (current) paragraphs.push(current)
  return paragraphs
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
        <span v-if="isUser" class="nickname-avatar">{{ userAvatarLabel }}</span>
        <span v-else-if="isError">!</span>
        <svg v-else class="medical-shield-icon" width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M12 3.2 5.5 5.8v5.4c0 4.1 2.7 7.8 6.5 9.2 3.8-1.4 6.5-5.1 6.5-9.2V5.8L12 3.2Z"></path>
          <path d="M6.8 12h2.5l1.5-4.3 3.1 8.6 1.5-4.3h1.8"></path>
        </svg>
      </div>

      <div class="message-body">
        <div class="message-text">
          <template v-for="(block, index) in formattedBlocks" :key="index">
            <p v-if="block.type === 'paragraph'" class="message-paragraph">
              {{ block.text }}
            </p>

            <ol v-else class="message-list-block">
              <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
                {{ item }}
              </li>
            </ol>
          </template>
        </div>

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
      background-color: var(--accent-brand);
      border: 1px solid var(--accent-primary);
      color: var(--bg-elevated);
    }
  }

  &.assistant {
    justify-content: flex-start;

    .message-text {
      background-color: var(--bg-secondary);
      border: 1px solid var(--border-subtle);
    }

    .message-avatar {
      width: 36px;
      height: 36px;
      background-color: transparent;
      border: 1px solid transparent;
      border-radius: 0;
      color: var(--accent-medical);
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

.message-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.message-text {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  line-height: 1.72;
  color: var(--text-primary);
  font-size: 0.9375rem;
  transition: all var(--transition-fast);
  max-width: min(760px, 100%);
  overflow-wrap: anywhere;

  &:hover {
    border-color: var(--border-default);
  }
}

.message-paragraph {
  margin: 0;

  & + .message-paragraph,
  & + .message-list-block {
    margin-top: var(--space-3);
  }
}

.message-list-block {
  margin: 0;
  padding-left: 1.35rem;

  & + .message-paragraph,
  & + .message-list-block {
    margin-top: var(--space-3);
  }

  li {
    padding-left: 0.2rem;

    & + li {
      margin-top: var(--space-2);
    }
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
