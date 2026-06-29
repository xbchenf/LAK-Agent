<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSessionMessages, sendSessionMessage, closeSession } from '@/api/operator'
import type { ContextMessage } from '@/api/operator'

const route = useRoute()
const router = useRouter()
const sessionId = route.params.sessionId as string

const messages = ref<ContextMessage[]>([])
const inputText = ref('')
const messagesEl = ref<HTMLElement>()
const loading = ref(false)
let ws: WebSocket | null = null

onMounted(async () => {
  loading.value = true
  try {
    const all = await getSessionMessages(sessionId)
    // 过滤系统消息（排队提示等），只保留实际对话
    messages.value = all.filter(m => m.role === 'user' || m.role === 'operator')
  } catch { /* ignore */ }
  finally { loading.value = false }
  startWebSocket()
})

onUnmounted(() => { if (ws) { ws.close(); ws = null } })

function startWebSocket() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/chat?sessionId=${sessionId}&role=operator`)
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.role && msg.content) {
        if (!messages.value.some(ex => ex.content === msg.content && ex.role === msg.role)) {
          messages.value.push(msg)
          scrollBottom()
        }
      }
    } catch { /* ignore */ }
  }
}

async function onSend() {
  const text = inputText.value.trim()
  if (!text) return
  inputText.value = ''
  try {
    await sendSessionMessage(sessionId, text)
    messages.value.push({ role: 'operator', content: text })
    scrollBottom()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '发送失败')
  }
}

async function onClose() {
  try {
    await closeSession(sessionId, '')
    ElMessage.success('会话已关闭')
    router.push('/operator')
  } catch { /* ignore */ }
}

function goBack() { router.push('/operator') }

function roleLabel(r: string) {
  if (r === 'user') return '用户'
  if (r === 'operator') return '坐席'
  if (r === 'assistant') return 'AI'
  return r
}

function scrollBottom() {
  nextTick(() => messagesEl.value?.scrollTo({ top: messagesEl.value.scrollHeight, behavior: 'smooth' }))
}
</script>

<template>
  <div class="op-chat">
    <div class="chat-topbar">
      <button class="back-btn" @click="goBack">← 返回工作台</button>
      <span class="session-label">会话: {{ sessionId?.substring(0, 12) }}...</span>
      <span class="spacer"></span>
      <button class="btn btn-close" @click="onClose">✕ 关闭会话</button>
    </div>

    <div ref="messagesEl" class="chat-messages" v-loading="loading">
      <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
        <div class="msg-label">{{ roleLabel(msg.role) }}</div>
        <div class="msg-bubble">{{ msg.content }}</div>
      </div>
      <div v-if="messages.length === 0 && !loading" class="empty">暂无消息</div>
    </div>

    <div class="chat-input">
      <textarea v-model="inputText" placeholder="输入回复…" rows="2" @keyup.enter.exact="onSend"></textarea>
      <button class="send-btn" @click="onSend">发送</button>
    </div>
  </div>
</template>

<style scoped>
.op-chat { display: flex; flex-direction: column; height: 100%; background: var(--color-bg); }
.chat-topbar {
  display: flex; align-items: center; gap: 12px; padding: 12px 20px;
  background: var(--color-bg-white); border-bottom: 1px solid var(--color-border); flex-shrink: 0;
}
.back-btn {
  padding: 6px 14px; border-radius: 20px; border: 1px solid var(--color-border);
  background: var(--color-bg-white); color: var(--color-text-secondary);
  font-size: 12px; cursor: pointer; font-family: inherit;
}
.back-btn:hover { border-color: var(--color-primary-light); color: var(--color-primary); }
.session-label { font-size: 13px; color: var(--color-text-secondary); }
.spacer { flex: 1; }
.btn-close {
  padding: 6px 14px; border-radius: var(--radius); border: 1px solid #fecaca;
  background: var(--color-bg-white); color: var(--color-danger);
  font-size: 12px; cursor: pointer; font-family: inherit;
}
.btn-close:hover { background: #fef2f2; }

.chat-messages {
  flex: 1; overflow-y: auto; padding: 20px 24px;
  display: flex; flex-direction: column; gap: 12px;
}
.msg { display: flex; flex-direction: column; gap: 2px; max-width: 80%; }
.msg.user { align-self: flex-start; }
.msg.assistant { align-self: flex-start; }
.msg.operator { align-self: flex-end; }
.msg-label { font-size: 10px; color: var(--color-text-muted); padding: 0 4px; }
.msg-bubble {
  padding: 10px 16px; border-radius: 12px; font-size: 13px; line-height: 1.6;
}
.msg.user .msg-bubble, .msg.assistant .msg-bubble {
  background: var(--color-bg-white); color: var(--color-text);
  border: 1px solid var(--color-border-light); border-bottom-left-radius: 2px;
}
.msg.operator .msg-bubble {
  background: var(--color-primary); color: #fff;
  border-bottom-right-radius: 2px;
}

.chat-input {
  padding: 14px 20px; background: var(--color-bg-white);
  border-top: 1px solid var(--color-border); display: flex; gap: 10px; flex-shrink: 0;
}
.chat-input textarea {
  flex: 1; padding: 10px 14px; border: 1px solid var(--color-border);
  border-radius: var(--radius); font-size: 13px; font-family: inherit;
  resize: none; outline: none; color: var(--color-text);
}
.chat-input textarea:focus { border-color: var(--color-primary-light); }
.send-btn {
  padding: 8px 20px; border: none; border-radius: var(--radius);
  background: var(--color-primary); color: #fff; font-size: 13px; font-weight: 500;
  cursor: pointer; font-family: inherit; white-space: nowrap;
}
.send-btn:hover { background: var(--color-primary-hover); }
.empty { text-align: center; color: var(--color-text-muted); padding: 40px; }
</style>
