<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const mode = ref('login')

const form = reactive({
  username: '',
  password: '',
  displayName: ''
})

async function handleSubmit() {
  if (!form.username.trim() || !form.password.trim() || authStore.isLoading) return
  if (mode.value === 'register') {
    await authStore.register(form.username.trim(), form.password, form.displayName.trim())
  } else {
    await authStore.login(form.username.trim(), form.password)
  }
  router.replace(route.query.redirect || '/')
}

function switchMode(nextMode) {
  mode.value = nextMode
  authStore.error = ''
}
</script>

<template>
  <main class="login-page">
    <section class="login-shell">
      <div class="brand-panel">
        <div class="brand-mark">
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 12h-4l-3 9L9 3l-3 9H2"></path>
          </svg>
        </div>
        <h1>医疗知识问答系统</h1>
        <p>登录后访问 RAG 与 kgdrug 知识图谱混合问答、知识库管理和历史会话。</p>
        <div class="signal-list">
          <span>RAG 检索</span>
          <span>kgdrug 图谱</span>
          <span>会话留存</span>
        </div>
      </div>

      <form class="login-card" @submit.prevent="handleSubmit">
        <div class="form-head">
          <span class="eyebrow">Secure access</span>
          <h2>{{ mode === 'login' ? '登录' : '注册账号' }}</h2>
        </div>

        <div class="mode-tabs" aria-label="认证方式">
          <button
            type="button"
            :class="{ active: mode === 'login' }"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            type="button"
            :class="{ active: mode === 'register' }"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <label class="field">
          <span>用户名</span>
          <input v-model="form.username" autocomplete="username" placeholder="3-32 位字母、数字或下划线" />
        </label>

        <label v-if="mode === 'register'" class="field">
          <span>昵称</span>
          <input v-model="form.displayName" autocomplete="nickname" placeholder="可选，用于顶部栏显示" />
        </label>

        <label class="field">
          <span>密码</span>
          <input
            v-model="form.password"
            type="password"
            :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
            :placeholder="mode === 'register' ? '至少 6 位密码' : '请输入密码'"
          />
        </label>

        <p v-if="authStore.error" class="error-text">{{ authStore.error }}</p>

        <button class="login-btn" type="submit" :disabled="authStore.isLoading">
          <span>{{ authStore.isLoading ? '处理中...' : (mode === 'login' ? '进入系统' : '创建并进入') }}</span>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M5 12h14"></path>
            <path d="m12 5 7 7-7 7"></path>
          </svg>
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-8);
  background:
    radial-gradient(circle at 20% 15%, rgba(71, 100, 90, 0.13), transparent 24rem),
    radial-gradient(circle at 85% 80%, rgba(123, 95, 62, 0.12), transparent 26rem),
    var(--bg-primary);
}

.login-shell {
  width: min(960px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: var(--space-6);
  align-items: stretch;
}

.brand-panel,
.login-card {
  border: 1px solid var(--border-subtle);
  background: rgba(255, 253, 248, 0.75);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(18px);
}

.brand-panel {
  padding: var(--space-8);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  min-height: 440px;
}

.brand-mark {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-xl);
  background: var(--accent-primary);
  color: var(--bg-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: auto;
}

.brand-panel h1 {
  font-size: clamp(2rem, 4vw, 3.35rem);
  line-height: 1.08;
  color: var(--text-primary);
  margin-bottom: var(--space-4);
  letter-spacing: 0;
}

.brand-panel p {
  color: var(--text-secondary);
  font-size: 1rem;
  line-height: 1.8;
  max-width: 520px;
}

.signal-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-6);
}

.signal-list span {
  padding: 6px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  background: var(--bg-soft);
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.login-card {
  padding: var(--space-8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--space-4);
}

.form-head {
  margin-bottom: var(--space-2);
}

.mode-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 3px;
  padding: 3px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: rgba(240, 234, 224, 0.5);
}

.mode-tabs button {
  height: 36px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-weight: 700;
}

.mode-tabs button.active {
  background: var(--bg-elevated);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.eyebrow {
  display: block;
  color: var(--accent-clinical);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: var(--space-2);
}

.form-head h2 {
  font-size: 1.55rem;
  color: var(--text-primary);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  color: var(--text-secondary);
  font-size: 0.9rem;
  font-weight: 600;
}

.field input {
  height: 48px;
  border-radius: var(--radius-lg);
  background: var(--bg-input);
}

.error-text {
  color: var(--accent-danger);
  font-size: 0.86rem;
  line-height: 1.5;
}

.login-btn {
  height: 48px;
  border-radius: var(--radius-lg);
  background: var(--accent-primary);
  color: var(--bg-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  font-weight: 700;
  margin-top: var(--space-2);
}

.login-btn:hover:not(:disabled) {
  background: var(--accent-secondary);
  transform: translateY(-1px);
}

.login-btn:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

@media (max-width: 820px) {
  .login-page {
    padding: var(--space-4);
  }

  .login-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: auto;
    padding: var(--space-6);
  }

  .brand-mark {
    margin-bottom: var(--space-8);
  }

  .login-card {
    padding: var(--space-6);
  }
}
</style>
