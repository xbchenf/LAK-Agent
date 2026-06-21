import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse, LoginVO } from '@/types'

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

let isRefreshing = false
let refreshQueue: Array<{ resolve: (v: any) => void; reject: (e: any) => void }> = []

async function tryRefreshToken(): Promise<string | null> {
  const auth = useAuthStore()
  if (!auth.refreshToken) return null
  try {
    const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken: auth.refreshToken })
    const vo = data.data as LoginVO
    auth.setAuth(vo)
    return vo.accessToken
  } catch {
    auth.logout()
    return null
  }
}

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
  async (error) => {
    const original = error.config
    const status = error.response?.status

    // 401 → 尝试刷新 Token
    if (status === 401 && !original._retry && !original.url?.includes('/auth/refresh') && !original.url?.includes('/auth/login')) {
      original._retry = true

      if (!isRefreshing) {
        isRefreshing = true
        const newToken = await tryRefreshToken()
        isRefreshing = false
        if (newToken) {
          original.headers.Authorization = `Bearer ${newToken}`
          refreshQueue.forEach(q => q.resolve(newToken))
          refreshQueue = []
          return instance(original)
        }
        refreshQueue.forEach(q => q.reject(new Error('refresh failed')))
        refreshQueue = []
        return Promise.reject(error)
      }

      // 已有刷新进行中，排队等待
      return new Promise((resolve, reject) => {
        refreshQueue.push({
          resolve: (token: string) => {
            original.headers.Authorization = `Bearer ${token}`
            resolve(instance(original))
          },
          reject,
        })
      })
    }

    const backendMsg = error.response?.data?.message
ElMessage.error(backendMsg || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default instance
