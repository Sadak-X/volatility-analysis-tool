import axios from 'axios'
import { useAuthStore } from '../stores/auth'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
  timestamp: string
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

http.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

http.interceptors.response.use((response) => response.data, async (error) => {
  if ([401, 403].includes(error.response?.status)) {
    const authStore = useAuthStore()
    authStore.clear()

    if (window.location.pathname !== '/login') {
      const redirect = `${window.location.pathname}${window.location.search}`
      window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`
    }
  }

  if (error.response?.data instanceof Blob) {
    const text = await error.response.data.text()
    try {
      const data = JSON.parse(text)
      return Promise.reject(new Error(data?.message || error.message || '请求失败'))
    } catch {
      return Promise.reject(new Error(text || error.message || '请求失败'))
    }
  }

  const message = error.response?.data?.message || error.message || '请求失败'
  return Promise.reject(new Error(message))
})
