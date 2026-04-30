<script setup>
import { computed, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()

onMounted(() => {
  chatStore.loadSessions()
})

const sortedSessions = computed(() => {
  return [...chatStore.sessions].sort((a, b) => getSessionTime(b) - getSessionTime(a))
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

function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const date = Array.isArray(dateStr)
    ? new Date(dateStr[0], dateStr[1] - 1, dateStr[2], dateStr[3] || 0, dateStr[4] || 0, dateStr[5] || 0)
    : new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function handleDelete(sessionId) {
  await chatStore.removeSession(sessionId)
}

function viewSession(session) {
  chatStore.loadHistory(session.sessionId)
  window.location.href = '/'
}
</script>

<template>
  <div class="history-view">
    <div class="page-header">
      <h1 class="page-title">历史会话</h1>
    </div>

    <div class="history-list">
      <div v-if="sortedSessions.length === 0" class="empty-state">
        <div class="empty-icon">·</div>
        <h3>暂无记录</h3>
        <p>开始一个新会话吧</p>
        <el-button type="primary" @click="$router.push('/')">开始问答</el-button>
      </div>

      <div v-else class="sessions-grid">
        <div
          v-for="(session, index) in sortedSessions"
          :key="session.sessionId"
          class="session-card"
          :style="{ animationDelay: `${index * 30}ms` }"
        >
          <div class="session-info">
            <h3 class="session-title">{{ session.title || '新会话' }}</h3>
            <span class="session-date">{{ formatDateTime(session.updatedAt) }}</span>
          </div>
          <div class="session-actions">
            <el-button size="small" text @click="viewSession(session)">查看</el-button>
            <el-button
              size="small"
              text
              type="danger"
              @click="handleDelete(session.sessionId)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.history-view {
  max-width: var(--max-content-width);
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--space-8);
  animation: fadeIn var(--transition-normal) ease;
}

.page-title {
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12);
  text-align: center;
  animation: fadeIn var(--transition-normal) ease;

  .empty-icon {
    font-size: 3rem;
    font-weight: 200;
    color: var(--text-muted);
    margin-bottom: var(--space-4);
  }

  h3 {
    color: var(--text-primary);
    margin-bottom: var(--space-2);
  }

  p {
    color: var(--text-secondary);
    margin-bottom: var(--space-6);
  }
}

.sessions-grid {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.session-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  transition: all var(--transition-fast);
  opacity: 0;
  animation: fadeIn var(--transition-normal) ease forwards;

  &:hover {
    border-color: var(--border-default);
    box-shadow: var(--shadow-sm);
  }
}

.session-info {
  min-width: 0;
}

.session-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--space-1);
}

.session-date {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.session-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .session-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .session-actions {
    width: 100%;
    padding-top: var(--space-3);
    border-top: 1px solid var(--border-subtle);
  }
}
</style>
