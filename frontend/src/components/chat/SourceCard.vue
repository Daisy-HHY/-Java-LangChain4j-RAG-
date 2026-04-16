<script setup>
import { ref } from 'vue'

const props = defineProps({
  sources: {
    type: Array,
    default: () => []
  }
})

const isExpanded = ref(false)

function toggle() {
  isExpanded.value = !isExpanded.value
}
</script>

<template>
  <div class="source-card" :class="{ expanded: isExpanded }">
    <div class="source-header" @click="toggle">
      <span class="source-label">来源</span>
      <span class="source-count">{{ sources.length }}</span>
      <el-icon class="expand-icon" :class="{ rotated: isExpanded }">
        <ArrowDown />
      </el-icon>
    </div>

    <div v-show="isExpanded" class="source-list">
      <div
        v-for="(source, index) in sources"
        :key="index"
        class="source-item"
        :style="{ animationDelay: `${index * 50}ms` }"
      >
        <div class="source-content">{{ source.content }}</div>
        <div class="source-score">{{ (source.score * 100).toFixed(0) }}%</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.source-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-top: var(--space-2);
  transition: all var(--transition-fast);
}

.source-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  transition: background-color var(--transition-fast);

  &:hover {
    background-color: var(--bg-hover);
  }
}

.source-label {
  font-size: 0.75rem;
  color: var(--text-muted);
  font-weight: 500;
}

.source-count {
  font-size: 0.75rem;
  color: var(--text-muted);
  background-color: var(--bg-hover);
  padding: 1px 6px;
  border-radius: 10px;
}

.expand-icon {
  margin-left: auto;
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
}

.source-item {
  padding: var(--space-2) var(--space-3);
  background: var(--bg-primary);
  border-radius: var(--radius-sm);
  opacity: 0;
  animation: fadeIn var(--transition-normal) ease forwards;
}

.source-content {
  color: var(--text-secondary);
  font-size: 0.8125rem;
  line-height: 1.5;
  margin-bottom: var(--space-1);
}

.source-score {
  font-size: 0.6875rem;
  color: var(--text-muted);
  font-family: var(--font-mono);
}
</style>
