---
name: vue-api-integration
description: Axios 封装 + LAK-Agent 后端接口调用模板。适配 ApiResponse<T> 解包 + JWT 注入 + SSE 流式。
---

# Vue API 集成规范（LAK-Agent）

## 触发条件

当需要新增或修改后端接口调用代码时，自动应用此 Skill。

## Axios 实例（`api/request.ts`）

```typescript
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

// 请求拦截: 自动注入 JWT Token
instance.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  config.headers['X-Trace-Id'] = crypto.randomUUID().replace(/-/g, '')
  return config
})

// 响应拦截: 解包 ApiResponse<T>
instance.interceptors.response.use(
  (response) => {
    const { code, message, data, traceId } = response.data
    if (code !== 200) {
      // 401 → 跳转登录
      if (code === 401) {
        useAuthStore().logout()
        return Promise.reject(new Error(message))
      }
      ElMessage.error(message || '请求失败')
      return Promise.reject(new Error(message))
    }
    return data  // 只返回 data 部分，调用方无需 .data.data
  },
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore().logout()
    }
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default instance
```

## 业务 API 封装

```typescript
// api/chat.ts
import request from './request'
import type { PageResult } from '@/types'

export interface ChatMessageVO {
  sessionId: string
  role: 'user' | 'assistant'
  content: string
  sources?: SourceDoc[]
  intentType?: string
  confidence?: number
}

export interface SessionVO {
  sessionId: string
  title: string
  intentType: string
  status: string
  createTime: string
  messageCount: number
}

/** 发送消息（JSON 模式） */
export function sendMessage(sessionId: string | null, message: string) {
  return request.post<ChatMessageVO>('/chat/message', { sessionId, message })
}

/** 获取会话列表（分页） */
export function listSessions(params: { page: number; size: number }) {
  return request.get<PageResult<SessionVO>>('/chat/sessions', { params })
}

/** 获取会话历史 */
export function getSession(sessionId: string) {
  return request.get<SessionVO>(`/chat/sessions/${sessionId}`)
}
```

## 强制约束

- 所有后端接口调用通过 `api/xxx.ts` 封装，禁止在 `.vue` 中直接 `import axios`
- 请求参数和响应必须 TypeScript 类型化
- 拦截器自动解包 `ApiResponse.data`，调用方直接拿业务数据
- 401 自动跳登录（不弹多个错误提示）
- `X-Trace-Id` 每个请求自动生成，用于全链路追踪
