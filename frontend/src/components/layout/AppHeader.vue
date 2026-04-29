<script setup>
import { useRouter, useRoute } from 'vue-router'

const emit = defineEmits(['toggle-sidebar'])

const router = useRouter()
const route = useRoute()

const navItems = [
  { path: '/', name: '问答' },
  { path: '/knowledge', name: '知识库' },
  { path: '/history', name: '历史' }
]

function isActive(path) {
  return route.path === path
}
</script>

<template>
  <header class="app-header">
    <div class="header-left">
      <button class="icon-btn" @click="emit('toggle-sidebar')" title="切换侧边栏">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="3" y1="6" x2="21" y2="6"></line>
          <line x1="3" y1="12" x2="21" y2="12"></line>
          <line x1="3" y1="18" x2="21" y2="18"></line>
        </svg>
      </button>
      <div class="logo" @click="router.push('/')">
        <span class="logo-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 12h-4l-3 9L9 3l-3 9H2"></path>
          </svg>
        </span>
        <span class="logo-text">医疗问答</span>
      </div>
    </div>

    <nav class="header-nav">
      <router-link
        v-for="(item, index) in navItems"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        :class="{ active: isActive(item.path) }"
        :style="{ animationDelay: `${index * 50}ms` }"
      >
        <span>{{ item.name }}</span>
      </router-link>
    </nav>

    <div class="header-right">
      <button class="icon-btn" title="设置">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3"></circle>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
        </svg>
      </button>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 var(--space-5);
  background-color: rgba(247, 243, 236, 0.88);
  border-bottom: 1px solid var(--border-subtle);
  position: sticky;
  top: 0;
  z-index: 100;
  backdrop-filter: blur(16px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.icon-btn {
  width: 34px;
  height: 34px;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.icon-btn:hover {
  color: var(--text-primary);
  background-color: var(--bg-hover);
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
}

.logo-icon {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-md);
  background: var(--accent-primary);
  color: var(--bg-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform var(--transition-fast);
}

.logo:hover .logo-icon {
  transform: translateY(-1px);
}

.logo-text {
  font-family: var(--font-display);
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: 0;
}

.header-nav {
  display: flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: rgba(255, 253, 248, 0.55);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 7px var(--space-4);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 0.85rem;
  font-weight: 500;
  transition: all var(--transition-fast);
  text-decoration: none;
  opacity: 0;
  animation: fadeIn var(--transition-normal) ease forwards;
  position: relative;
}

.nav-item:hover {
  color: var(--text-primary);
  background-color: rgba(235, 227, 214, 0.7);
}

.nav-item.active {
  color: var(--text-primary);
  background-color: var(--bg-elevated);
  box-shadow: var(--shadow-sm);
}

.nav-item.active::after {
  content: none;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 768px) {
  .logo-text {
    display: none;
  }

  .nav-item span {
    display: none;
  }

  .header-nav {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background-color: rgba(247, 243, 236, 0.95);
    border-top: 1px solid var(--border-subtle);
    border-left: 0;
    border-right: 0;
    border-bottom: 0;
    border-radius: 0;
    padding: var(--space-2);
    justify-content: space-around;
    z-index: 100;
    backdrop-filter: blur(16px);
  }

  .app-header {
    padding-inline: var(--space-4);
  }
}
</style>
