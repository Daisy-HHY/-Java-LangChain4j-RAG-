import apiClient from './index'

export function login(username, password) {
  return apiClient.post('/auth/login', { username, password })
}

export function register(username, password, displayName = '') {
  return apiClient.post('/auth/register', { username, password, displayName })
}

export function getCurrentUser() {
  return apiClient.get('/auth/me')
}

export function logout() {
  return apiClient.post('/auth/logout')
}
