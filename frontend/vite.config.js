import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env }
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      port: 5173,
      strictPort: true,
      watch: {
        usePolling: env.VITE_USE_POLLING === 'true'
      },
      proxy: {
        '/api': {
          target: env.API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true
        },
        '/uploads': {
          target: env.API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})
