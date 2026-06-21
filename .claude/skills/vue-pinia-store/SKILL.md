---
name: vue-pinia-store
description: Pinia 状态管理模板 — LAK-Agent 三大 Store: auth/chat/app
---

# Vue Pinia 状态管理规范（LAK-Agent）

## 项目 Store 规划

| Store | 文件 | 职责 |
|-------|------|------|
| `useAuthStore` | `stores/auth.ts` | 用户认证状态、Token、角色 |
| `useChatStore` | `stores/chat.ts` | 当前会话、消息列表、流式状态 |
| `useAppStore` | `stores/app.ts` | 全局 UI 状态（侧边栏折叠、加载遮罩） |

## 模板（Composition API 风格）

```typescript
// stores/useChatStore.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { sendMessage, getSession } from '@/api/chat'
import type { ChatMessageVO } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  // State
  const messages = ref<ChatMessageVO[]>([])
  const currentSessionId = ref<string | null>(null)
  const isStreaming = ref(false)

  // Getters
  const lastMessage = computed(() => messages.value.at(-1) ?? null)
  const messageCount = computed(() => messages.value.length)

  // Actions
  async function send(message: string) {
    messages.value.push({ role: 'user', content: message, sessionId: '' })
    isStreaming.value = true
    try {
      const result = await sendMessage(currentSessionId.value, message)
      messages.value.push(result)
      if (!currentSessionId.value) {
        currentSessionId.value = result.sessionId
      }
    } finally {
      isStreaming.value = false
    }
  }

  function clearMessages() {
    messages.value = []
    currentSessionId.value = null
  }

  return { messages, currentSessionId, isStreaming, lastMessage, messageCount, send, clearMessages }
})
```

## 强制约束

- 必须 Composition API 风格: `defineStore('id', () => { ... })`
- State 用 `ref`, Getters 用 `computed`, Actions 用普通函数
- 禁止在组件内直接修改 store state（通过 action）
- 敏感数据（Token）仅在内存（Pinia），禁止存 localStorage/sessionStorage
