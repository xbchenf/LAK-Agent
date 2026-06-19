import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse } from '@/types'

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

instance.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  config.headers['X-Trace-Id'] = crypto.randomUUID().replace(/-/g, '')
  return config
})

instance.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data as ApiResponse
    if (code !== 200) {
      ElMessage.error(message || '请求失败')
      return Promise.reject(new Error(message))
    }
    return data as any
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      const url = error.config?.url || ''
      // 仅核心接口401才触发登出，避免查询类接口误踢
      if (url.includes('/chat/message') || url.includes('/auth/refresh')) {
        useAuthStore().logout()
      }
    }
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default instance
