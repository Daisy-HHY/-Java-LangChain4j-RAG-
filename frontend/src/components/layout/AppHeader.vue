<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits(['toggle-sidebar'])

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const navItems = [
  { path: '/', name: '问答' },
  { path: '/knowledge', name: '知识库' },
  { path: '/history', name: '历史' }
]

function isActive(path) {
  return route.path === path
}

async function handleLogout() {
  await authStore.logout()
  router.replace('/login')
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
      <div class="user-chip" :title="authStore.user?.username">
        <span class="user-avatar">{{ (authStore.user?.displayName || authStore.user?.username || 'U').slice(0, 1) }}</span>
        <span class="user-name">{{ authStore.user?.displayName || authStore.user?.username || '用户' }}</span>
      </div>
      <button class="icon-btn" title="退出登录" @click="handleLogout">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
          <polyline points="16 17 21 12 16 7"></polyline>
          <line x1="21" y1="12" x2="9" y2="12"></line>
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

.user-chip {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 34px;
  padding: 3px 10px 3px 4px;
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  background: rgba(255, 253, 248, 0.58);
  color: var(--text-secondary);
  font-size: 0.82rem;
  max-width: 180px;
}

.user-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--accent-clinical);
  color: var(--bg-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.78rem;
  font-weight: 700;
}

.user-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
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

  .user-name {
    display: none;
  }

  .user-chip {
    padding-right: 4px;
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
