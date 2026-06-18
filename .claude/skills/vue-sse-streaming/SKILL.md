---
name: vue-sse-streaming
description: SSE 流式对话核心交互 — Chat API 流式接收 + 逐字渲染 + 溯源卡片 + 中断重连
---

# SSE 流式对话规范（LAK-Agent）

## 触发条件

对话页面的 SSE 流式消息接收和渲染，自动应用此 Skill。

## SSE 连接管理

```typescript
// utils/sse.ts
import { fetchEventSource } from '@microsoft/fetch-event-source'

interface SSEMessageEvent {
  content: string       // 增量文本
  sources?: SourceDoc[] // 溯源文档（仅 done 事件携带）
  sessionId: string
  intentType?: string
  confidence?: number
}

export function connectSSE(
  sessionId: string | null,
  message: string,
  accessToken: string,
  callbacks: {
    onMessage: (delta: string) => void
    onDone: (full: SSEMessageEvent) => void
    onError: (error: Error) => void
  }
) {
  const controller = new AbortController()

  fetchEventSource('/api/v1/chat/message', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${accessToken}`,
      'Accept': 'text/event-stream',
    },
    body: JSON.stringify({ sessionId, message }),
    signal: controller.signal,
    onmessage(event) {
      if (event.event === 'message') {
        const data = JSON.parse(event.data)
        callbacks.onMessage(data.content)
      } else if (event.event === 'done') {
        const data = JSON.parse(event.data)
        callbacks.onDone(data)
      }
    },
    onerror(error) {
      callbacks.onError(error)
      throw error  // 不自动重连
    },
  })

  return controller  // 调用方通过 controller.abort() 中断
}
```

## StreamingText 组件

```vue
<!-- components/chat/StreamingText.vue -->
<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

const props = defineProps<{ text: string; isStreaming: boolean }>()
const container = ref<HTMLElement>()

// 自动滚动到底部
watch(() => props.text, async () => {
  await nextTick()
  container.value?.scrollIntoView({ behavior: 'smooth' })
})
</script>

<template>
  <div ref="container" class="streaming-text" :class="{ 'is-streaming': isStreaming }">
    {{ text }}
    <span v-if="isStreaming" class="cursor-blink">|</span>
  </div>
</template>
```

## SourceCitation 溯源卡片

```vue
<!-- components/chat/SourceCitation.vue -->
<script setup lang="ts">
import type { SourceDoc } from '@/api/chat'

defineProps<{ sources: SourceDoc[] }>()
</script>

<template>
  <div class="source-citation">
    <div class="source-title">📄 答复依据</div>
    <div v-for="doc in sources" :key="doc.docId" class="source-item">
      <div class="source-header">
        <el-tag size="small">{{ doc.sourceNo }}</el-tag>
        <span class="source-name">{{ doc.title }}</span>
        <span v-if="doc.effectiveDate" class="source-date">
          生效: {{ doc.effectiveDate }}
        </span>
      </div>
      <blockquote>{{ doc.fragment }}</blockquote>
    </div>
  </div>
</template>
```

## 强制约束

- SSE 使用 `@microsoft/fetch-event-source`（支持 POST + 自定义 Header，比原生 EventSource 强）
- 流式期间显示闪烁光标 `|`，done 事件后消失
- 敏感词拦截时清空已渲染的流式内容（由后端 error 事件触发）
- 用户发送新消息时调用 `controller.abort()` 中断当前 SSE
- 溯源文档至少展示: 文件编号(sourceNo) + 标题(title) + 生效日期(effectiveDate) + 原文片段(fragment)
