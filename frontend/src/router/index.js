import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue')
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/KnowledgeView.vue')
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/HistoryView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
