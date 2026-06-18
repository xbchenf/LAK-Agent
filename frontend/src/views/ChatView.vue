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
    const result = await sendMessage(chat.currentSessionId, text)
    const last = chat.messages.at(-1)
    if (last) {
      last.content = result.answer || ''
      last.sources = result.sources as unknown as SourceDoc[] || []
      last.isStreaming = false
      last.sessionId = result.sessionId
    }
    if (result.sessionId && !chat.currentSessionId) {
      chat.currentSessionId = result.sessionId
    }
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
.chat-view { display: flex; flex-direction: column; height: calc(100vh - 80px); }
.messages { flex: 1; overflow-y: auto; padding: 16px; }
.message { margin-bottom: 16px; display: flex; }
.message.user { justify-content: flex-end; }
.message.user .bubble { background: #ecf5ff; border-radius: 12px 12px 0 12px; }
.message.assistant .bubble { background: #f5f7fa; border-radius: 12px 12px 12px 0; }
.bubble { max-width: 75%; padding: 12px 16px; }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.input-area { padding: 12px 16px; border-top: 1px solid #ebeef5; background: white; }
</style>
