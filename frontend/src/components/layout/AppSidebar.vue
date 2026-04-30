<script setup>
import { computed, ref, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'

defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

const chatStore = useChatStore()

const sortedSessions = computed(() => {
  return [...chatStore.sessions].sort((a, b) => getSessionTime(b) - getSessionTime(a))
})

onMounted(() => {
  chatStore.loadSessions()
})

function getSessionTime(session) {
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

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = Array.isArray(dateStr)
    ? new Date(dateStr[0], dateStr[1] - 1, dateStr[2], dateStr[3] || 0, dateStr[4] || 0, dateStr[5] || 0)
    : new Date(dateStr)
  const now = new Date()
  const diff = now - date

  if (diff < 86400000) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  if (diff < 604800000) {
    const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return days[date.getDay()]
  }
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function selectSession(session) {
  chatStore.loadHistory(session.sessionId)
}

async function deleteSession(session, event) {
  event.stopPropagation()
  await chatStore.removeSession(session.sessionId)
}

function createNewSession() {
  chatStore.clearCurrentMessages()
}
</script>

<template>
  <aside class="app-sidebar" :class="{ collapsed }">
    <div class="sidebar-header">
      <h3 class="sidebar-title">会话</h3>
      <button class="icon-btn" @click="createNewSession" title="新建会话">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"></line>
          <line x1="5" y1="12" x2="19" y2="12"></line>
        </svg>
      </button>
    </div>

    <div class="session-list">
      <div
        v-for="(session, index) in sortedSessions"
        :key="session.sessionId"
        class="session-item"
        :class="{ active: chatStore.currentSessionId === session.sessionId }"
        @click="selectSession(session)"
      >
        <div class="session-indicator"></div>
        <div class="session-info">
          <span class="session-title">{{ session.title || '新会话' }}</span>
          <span class="session-date">{{ formatDate(session.updatedAt) }}</span>
        </div>
        <button
          class="delete-btn"
          @click="deleteSession(session, $event)"
          title="删除会话"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <div v-if="sortedSessions.length === 0" class="empty-state">
        <div class="empty-dot"></div>
        <p>暂无会话</p>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.app-sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  background-color: rgba(240, 234, 224, 0.72);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal), min-width var(--transition-normal);
  overflow: hidden;
  backdrop-filter: blur(16px);

  &.collapsed {
    width: 0;
    min-width: 0;
    border-right: none;
  }
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4);
  border-bottom: 1px solid var(--border-subtle);
}

.sidebar-title {
  font-family: var(--font-display);
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-subtle);
  background: var(--bg-elevated);
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.icon-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
  transform: translateY(-1px);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-3);
}

.session-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 10px var(--space-3);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: 2px;
  position: relative;
  border: 1px solid transparent;

  &:hover {
    background-color: rgba(255, 253, 248, 0.62);

    .delete-btn {
      opacity: 1;
    }
  }

  &.active {
    background-color: var(--bg-elevated);
    border-color: var(--border-subtle);
    box-shadow: var(--shadow-sm);

    .session-indicator {
      opacity: 1;
      transform: scaleY(1);
    }
  }
}

.session-indicator {
  position: absolute;
  left: 6px;
  top: 50%;
  transform: translateY(-50%) scaleY(0);
  width: 4px;
  height: 4px;
  background: var(--accent-clinical);
  border-radius: 50%;
  opacity: 0;
  transition: all var(--transition-fast);
}

.session-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}

.session-title {
  font-size: 0.88rem;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
}

.session-date {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.delete-btn {
  opacity: 0;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: all var(--transition-fast);
}

.delete-btn:hover {
  color: var(--accent-danger);
  background-color: rgba(155, 77, 70, 0.1);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12) var(--space-4);
  color: var(--text-muted);
  text-align: center;
}

.empty-state p {
  margin-top: var(--space-3);
  font-size: 0.8125rem;
}

.empty-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--border-default);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.4; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.2); }
}

@media (max-width: 768px) {
  .app-sidebar {
    position: fixed;
    left: 0;
    top: var(--header-height);
    bottom: 60px;
    z-index: 99;
    background-color: rgba(240, 234, 224, 0.96);

    &.collapsed {
      transform: translateX(-100%);
    }
  }
}
</style>
