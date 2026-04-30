import axios from 'axios'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
apiClient.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
apiClient.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('API Error:', error.message)
    if (error?.response?.status === 401 && window.location.pathname !== '/login') {
      localStorage.removeItem('kgqa_auth_user')
      localStorage.removeItem('kgqa_auth_expires_at')
      window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
    }
    return Promise.reject(error)
  }
)

export default apiClient
