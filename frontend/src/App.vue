<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from './components/layout/AppHeader.vue'
import AppSidebar from './components/layout/AppSidebar.vue'

const isSidebarCollapsed = ref(false)
const route = useRoute()
const isAuthLayout = computed(() => route.meta.authLayout)

function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}
</script>

<template>
  <div class="app-container" :class="{ 'auth-container': isAuthLayout }">
    <AppHeader v-if="!isAuthLayout" @toggle-sidebar="toggleSidebar" />

    <div v-if="!isAuthLayout" class="app-body">
      <AppSidebar :collapsed="isSidebarCollapsed" />
      <div class="app-page">
        <router-view />
      </div>
    </div>

    <router-view v-else />
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: var(--bg-primary);
  color: var(--text-primary);
}

#app {
  height: 100%;
}

.app-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background:
    radial-gradient(circle at top left, rgba(123, 95, 62, 0.08), transparent 28rem),
    var(--bg-primary);
}

.app-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.auth-container {
  display: block;
}

.app-page {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
</style>
