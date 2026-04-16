<script setup>
import { ref } from 'vue'

const emit = defineEmits(['send'])

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false
  }
})

const inputValue = ref('')

async function handleSend() {
  if (!inputValue.value.trim() || props.disabled) return

  emit('send', inputValue.value.trim())
  inputValue.value = ''
}

function handleKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="chat-input">
    <div class="input-wrapper">
      <el-input
        v-model="inputValue"
        type="textarea"
        :rows="2"
        placeholder="输入问题..."
        :disabled="disabled"
        resize="none"
        @keydown="handleKeydown"
      />

      <el-button
        class="send-btn"
        :disabled="disabled || !inputValue.trim()"
        @click="handleSend"
      >
        <el-icon><Promotion /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.chat-input {
  flex-shrink: 0;
  padding: var(--space-3) 0;
  border-top: 1px solid var(--border-subtle);
  background-color: var(--bg-primary);
}

.input-wrapper {
  display: flex;
  gap: var(--space-3);
  align-items: flex-end;
}

.input-wrapper :deep(.el-textarea) {
  flex: 1;
}

.input-wrapper :deep(.el-textarea__inner) {
  background-color: var(--bg-input);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  color: var(--text-primary);
  padding: var(--space-3) var(--space-4);
  font-family: var(--font-body);
  line-height: 1.6;
  font-size: 0.9375rem;
  transition: all var(--transition-fast);

  &::placeholder {
    color: var(--text-muted);
  }

  &:focus {
    border-color: var(--accent-primary);
    box-shadow: none;
  }
}

.send-btn {
  height: 42px;
  width: 42px;
  padding: 0;
  border-radius: var(--radius-lg);
  background-color: var(--accent-primary);
  border: none;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);

  .el-icon {
    font-size: 1rem;
  }

  &:hover:not(:disabled) {
    background-color: var(--accent-secondary);
    transform: scale(1.05);
  }

  &:disabled {
    background-color: var(--border-default);
    cursor: not-allowed;
  }
}
</style>
