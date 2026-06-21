<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { sendMessage } from '@/api/chat'
import type { SourceDoc } from '@/types'
import SourceCitation from '@/components/chat/SourceCitation.vue'

const chat = useChatStore()
const auth = useAuthStore()
const inputText = ref('')
const messagesEl = ref<HTMLElement>()

async function onSend() {
  const text = inputText.value.trim()
  if (!text || chat.isStreaming || text.length > 2000) return
  inputText.value = ''
  chat.addMessage({ role: 'user', content: text })
  chat.isStreaming = true
  chat.addMessage({ role: 'assistant', content: '', isStreaming: true })
  scrollBottom()

  try {
    const resp = await fetch('/api/v1/chat/message', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${auth.accessToken}`,
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify({ sessionId: chat.currentSessionId, message: text }),
    })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)

    const reader = resp.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    const last = chat.messages.at(-1)!

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (!data) continue
          try {
            const json = JSON.parse(data)
            if (json.sessionId) {
              if (!chat.currentSessionId) chat.currentSessionId = json.sessionId
              if (json.sources) last.sources = json.sources
            }
          } catch {
            // plain text token
            last.content += data
            scrollBottom()
          }
        } else if (line.startsWith('event:done')) {
          // handled by the data: line above (sources extracted from JSON)
        } else if (line.startsWith('event:error')) {
          last.content = last.content || '系统繁忙，请稍后重试'
        }
      }
    }
    last.isStreaming = false
  } catch {
    const last = chat.messages.at(-1)
    if (last) { last.content = '系统繁忙，请稍后重试'; last.isStreaming = false }
  } finally {
    chat.isStreaming = false
    scrollBottom()
  }
}

function scrollBottom() {
  nextTick(() => messagesEl.value?.scrollTo({ top: messagesEl.value.scrollHeight, behavior: 'smooth' }))
}

watch(() => chat.messages.length, scrollBottom)
</script>

<template>
  <div class="chat-view">
    <div class="chat-header">
      <button class="new-chat-btn" @click="chat.clearMessages()">＋ 新对话</button>
    </div>
    <div ref="messagesEl" class="messages">
      <div v-for="(msg, i) in chat.messages" :key="i" :class="['message', msg.role]">
        <div class="bubble">
          <div class="content" v-html="msg.content || '思考中...'" />
          <span v-if="msg.isStreaming" class="cursor">|</span>
          <SourceCitation v-if="msg.sources?.length" :sources="msg.sources" />
        </div>
      </div>
    </div>
    <div class="input-area">
      <el-input
        v-model="inputText"
        :maxlength="2000"
        placeholder="输入您的问题..."
        :disabled="chat.isStreaming"
        @keyup.enter="onSend"
      >
        <template #append>
          <el-button :loading="chat.isStreaming" @click="onSend">发送</el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<style scoped>
.chat-view { display: flex; flex-direction: column; height: 100vh; position: relative; }
.chat-header { padding: 8px 24px; border-bottom: 1px solid var(--color-border-light); background: var(--color-bg-white); }
.new-chat-btn {
  background: var(--color-accent); color: #fff; border: none;
  padding: 6px 16px; border-radius: 6px; cursor: pointer;
  font-size: 13px; font-weight: 500;
}
.new-chat-btn:hover { opacity: 0.85; }
.messages { flex: 1; overflow-y: auto; padding: 24px; max-width: 900px; margin: 0 auto; width: 100%; }
.message { margin-bottom: 20px; display: flex; }
.message.user { justify-content: flex-end; }
.message.user .bubble {
  background: var(--color-primary); color: #fff;
  border-radius: 16px 16px 4px 16px;
}
.message.assistant .bubble {
  background: var(--color-bg-white);
  border-radius: 16px 16px 16px 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.bubble { max-width: 75%; padding: 14px 18px; line-height: 1.7; font-size: 14px; white-space: pre-wrap; }
.cursor { animation: blink 1s infinite; color: var(--color-accent); }
@keyframes blink { 50% { opacity: 0; } }
.input-area {
  padding: 16px 24px; background: var(--color-bg-white);
  border-top: 1px solid var(--color-border);
}
.input-area :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--color-border); border-radius: 24px; padding: 4px 16px;
}
.input-area :deep(.el-input-group__append) {
  background: var(--color-primary); border: none; border-radius: 0 24px 24px 0; color: #fff;
}
</style>
