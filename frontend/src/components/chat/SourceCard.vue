<script setup>
import { ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  sources: {
    type: Array,
    default: () => []
  },
  initiallyExpanded: {
    type: Boolean,
    default: false
  }
})

const isExpanded = ref(props.initiallyExpanded)

function toggle() {
  isExpanded.value = !isExpanded.value
}

function getSourceContent(source) {
  if (typeof source === 'string') return source
  return source?.content || source?.text || source?.source || '暂无来源内容'
}

function getSourceScore(source) {
  const score = typeof source === 'object' ? Number(source?.score) : NaN
  return Number.isFinite(score) ? `${(score * 100).toFixed(0)}%` : 'N/A'
}
</script>

<template>
  <div class="source-card" :class="{ expanded: isExpanded }">
    <button
      type="button"
      class="source-header"
      :aria-expanded="isExpanded"
      @click="toggle"
    >
      <span class="source-label">来源</span>
      <span class="source-count">{{ sources.length }}</span>
      <span class="source-action">{{ isExpanded ? '收回' : '展开' }}</span>
      <el-icon class="expand-icon" :class="{ rotated: isExpanded }">
        <ArrowDown />
      </el-icon>
    </button>

    <div v-show="isExpanded" class="source-list">
      <div
        v-for="(source, index) in sources"
        :key="index"
        class="source-item"
        :style="{ animationDelay: `${index * 50}ms` }"
      >
        <div class="source-index">#{{ index + 1 }}</div>
        <div class="source-main">
          <div class="source-content">{{ getSourceContent(source) }}</div>
          <div class="source-score">相关度 {{ getSourceScore(source) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.source-card {
  background: rgba(255, 253, 248, 0.72);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-top: var(--space-4);
  transition: all var(--transition-fast);
  box-shadow: var(--shadow-sm);
}

.source-header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  transition: background-color var(--transition-fast);

  &:hover {
    background-color: rgba(235, 227, 214, 0.55);
  }
}

.source-label {
  font-size: 0.78rem;
  color: var(--text-secondary);
  font-weight: 600;
}

.source-count {
  font-size: 0.75rem;
  color: var(--text-muted);
  background-color: var(--bg-hover);
  padding: 1px 6px;
  border-radius: 10px;
}

.source-action {
  margin-left: auto;
  font-size: 0.75rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.expand-icon {
  font-size: 0.75rem;
  color: var(--text-muted);
  transition: transform var(--transition-fast);

  &.rotated {
    transform: rotate(180deg);
  }
}

.source-list {
  border-top: 1px solid var(--border-subtle);
  padding: var(--space-2);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  max-height: 260px;
  overflow-y: auto;
}

.source-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--bg-soft);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  opacity: 1;
  animation: sourceItemIn var(--transition-normal) ease both;
}

.source-index {
  color: var(--text-muted);
  font-size: 0.6875rem;
  font-family: var(--font-mono);
  padding-top: 2px;
}

.source-main {
  min-width: 0;
}

.source-content {
  color: var(--text-secondary);
  font-size: 0.8125rem;
  line-height: 1.5;
  margin-bottom: var(--space-1);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.source-score {
  font-size: 0.6875rem;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

@keyframes sourceItemIn {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
