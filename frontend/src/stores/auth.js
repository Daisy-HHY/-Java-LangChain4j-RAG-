import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getCurrentUser, login as loginApi, logout as logoutApi, register as registerApi } from '@/api/auth'

const USER_KEY = 'kgqa_auth_user'
const EXPIRES_KEY = 'kgqa_auth_expires_at'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(readStoredUser())
  const expiresAt = ref(Number(localStorage.getItem(EXPIRES_KEY) || 0))
  const hasCheckedSession = ref(false)
  const isLoading = ref(false)
  const error = ref('')

  const isAuthenticated = computed(() => Boolean(user.value) && expiresAt.value > Date.now())

  async function login(username, password) {
    isLoading.value = true
    error.value = ''
    try {
      const response = await loginApi(username, password)
      setSession(response.user, response.expiresAt)
      return response
    } catch (e) {
      error.value = e?.response?.data?.message || '登录失败，请检查用户名和密码'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function register(username, password, displayName) {
    isLoading.value = true
    error.value = ''
    try {
      const response = await registerApi(username, password, displayName)
      setSession(response.user, response.expiresAt)
      return response
    } catch (e) {
      error.value = e?.response?.data?.message || '注册失败，请检查输入信息'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function loadCurrentUser(force = false) {
    if (!force && hasCheckedSession.value) return user.value
    try {
      const profile = await getCurrentUser()
      user.value = profile
      if (!expiresAt.value || expiresAt.value <= Date.now()) {
        expiresAt.value = Date.now() + 60 * 60 * 1000
        localStorage.setItem(EXPIRES_KEY, String(expiresAt.value))
      }
      localStorage.setItem(USER_KEY, JSON.stringify(profile))
      return profile
    } finally {
      hasCheckedSession.value = true
    }
  }

  async function logout() {
    try {
      if (user.value) {
        await logoutApi()
      }
    } catch (e) {
      console.warn('Logout request failed:', e)
    } finally {
      clearSession()
    }
  }

  function setSession(nextUser, nextExpiresAt) {
    user.value = nextUser || null
    expiresAt.value = Number(nextExpiresAt || 0)
    hasCheckedSession.value = true
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    localStorage.setItem(EXPIRES_KEY, String(expiresAt.value))
  }

  function clearSession() {
    user.value = null
    expiresAt.value = 0
    hasCheckedSession.value = true
    localStorage.removeItem('kgqa_auth_token')
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(EXPIRES_KEY)
  }

  function readStoredUser() {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  }

  return {
    user,
    expiresAt,
    hasCheckedSession,
    isLoading,
    error,
    isAuthenticated,
    login,
    register,
    loadCurrentUser,
    logout,
    clearSession
  }
})
