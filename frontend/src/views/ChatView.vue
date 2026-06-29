<script setup lang="ts">
import { ref, nextTick, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import type { SlotState } from '@/stores/chat'
import SourceCitation from '@/components/chat/SourceCitation.vue'

const router = useRouter()
const chat = useChatStore()
const auth = useAuthStore()
const inputText = ref('')
const messagesEl = ref<HTMLElement>()
const isHumanHandling = ref(false)
let ws: WebSocket | null = null

onUnmounted(() => { if (ws) { ws.close(); ws = null } })

// 最近一条 assistant 消息的 slot 状态
const activeSlot = computed(() => {
  for (let i = chat.messages.length - 1; i >= 0; i--) {
    const s = chat.messages[i].slot
    if (s?.state === 'COLLECT_INFO' || s?.state === 'TICKET_SUBMIT') return s
  }
  return null
})

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
              if (json.extra) {
                last.slot = { ...last.slot, ...json.extra } as SlotState
              }
              if (json.state && !last.slot) {
                last.slot = { state: json.state } as SlotState
              }
              if (json.slotStage) {
                if (!last.slot) last.slot = {} as SlotState
                last.slot.slotStage = json.slotStage
              }
              if (json.redirectToManual) {
                if (!last.slot) last.slot = {} as SlotState
                last.slot.redirectToManual = true
                last.slot.prefilledSlots = json.prefilledSlots
              }
              if (json.ticketNo) {
                if (!last.slot) last.slot = {} as SlotState
                last.slot.ticketNo = json.ticketNo
                last.slot.state = 'TICKET_SUBMIT'
              }
              // 检测转人工状态
              if (json.state === 'WAITING_OPERATOR') {
                isHumanHandling.value = true
                last.isStreaming = false
                startPollingOperatorMessages(json.sessionId || chat.currentSessionId)
              }
              if (json.state === 'HUMAN_HANDLING') {
                isHumanHandling.value = true
                last.content = '坐席正在处理中...'
                last.isStreaming = false
                startPollingOperatorMessages(json.sessionId || chat.currentSessionId)
              }
            }
          } catch {
            last.content += data
            scrollBottom()
          }
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

function onResumeSlot() {
  const last = chat.messages.at(-1)
  if (last?.slot?.slotStage === 'interrupted') {
    last.slot = { state: 'COLLECT_INFO', slotStage: 'fill' }
  }
  scrollBottom()
}

function goToManualTicket(slot?: SlotState | null) {
  const prefill = (slot as any)?.prefilledSlots as Record<string, string> | undefined
  if (prefill && Object.keys(prefill).length > 0) {
    sessionStorage.setItem('ticket_prefill', JSON.stringify(prefill))
  }
  router.push('/tickets')
}

// 快捷按钮：等同于用户输入文本发送
function quickSend(text: string) {
  inputText.value = text
  onSend()
}

// 投诉类型选项列表
const complaintTypeOptions: { label: string; value: string }[] = [
  { label: '治安投诉（打架斗殴、噪音扰民、赌博等）', value: '治安投诉' },
  { label: '窗口服务投诉（户籍、身份证办理等）', value: '窗口服务投诉' },
  { label: '派出所/民警投诉', value: '派出所/民警投诉' },
  { label: '其他', value: '其他' },
]

function selectComplaintType(type: string) {
  inputText.value = type
  onSend()
}

// WebSocket 订阅坐席消息（用户端感知人工接管后的回复）
function startPollingOperatorMessages(sessionId: string) {
  if (ws) { ws.close(); ws = null }
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/chat?sessionId=${sessionId}&role=user`)
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.role === 'operator' && msg.content) {
        chat.addMessage({ role: 'assistant', content: msg.content })
        isHumanHandling.value = true
        scrollBottom()
      }
      if (msg.type === 'close') {
        isHumanHandling.value = false
        if (ws) { ws.close(); ws = null }
        chat.addMessage({ role: 'assistant', content: '会话已结束，如有需要请重新提问。' })
        scrollBottom()
      }
    } catch { /* ignore */ }
  }
  ws.onopen = () => { console.log('[ChatView] WebSocket 已连接') }
  ws.onerror = () => { console.warn('[ChatView] WebSocket 连接失败') }
  ws.onclose = () => { isHumanHandling.value = false; console.log('[ChatView] WebSocket 断开') }
}

function scrollBottom() {
  nextTick(() => messagesEl.value?.scrollTo({ top: messagesEl.value.scrollHeight, behavior: 'smooth' }))
}
</script>

<template>
  <div class="chat-view">
    <div class="chat-top">
      <button class="new-chat-btn" @click="chat.clearMessages()" :disabled="chat.isStreaming">＋ 新对话</button>
      <div v-if="isHumanHandling" class="human-banner-inline">已接入人工坐席</div>
    </div>

    <!-- Complaint type options -->

    <div ref="messagesEl" class="messages">
      <div v-for="(msg, i) in chat.messages" :key="i" :class="['message', msg.role]">
        <!-- Avatar -->
        <div class="msg-avatar">{{ msg.role === 'user' ? (auth.realName || auth.username || '我').charAt(0) : 'AI' }}</div>
        <div class="bubble">
          <div class="content" v-html="msg.content || '思考中...'" />
          <span v-if="msg.isStreaming" class="cursor">|</span>

          <!-- 投诉类型选项按钮 -->
          <div v-if="i === chat.messages.length - 1 && msg.role === 'assistant'
            && msg.slot?.state === 'COLLECT_INFO'
            && msg.slot?.slotStage !== 'interrupted' && msg.slot?.slotStage !== 'redirect' && msg.slot?.slotStage !== 'done'
            && msg.content.includes('哪类问题')" class="complaint-options-inline">
            <button v-for="t in complaintTypeOptions" :key="t.value" class="complaint-opt-btn-inline"
              :disabled="chat.isStreaming" @click="selectComplaintType(t.value)">{{ t.label }}</button>
          </div>

          <!-- Slot filling progress -->
          <div v-if="msg.slot?.state === 'COLLECT_INFO' && msg.slot.slotStage !== 'interrupted'" class="slot-progress">
            <span class="slot-label">📋 工单填写中</span>
          </div>

          <!-- Interrupted banner -->
          <div v-if="msg.slot?.state === 'COLLECT_INFO' && msg.slot.slotStage === 'interrupted'" class="interrupt-banner">
            <span>⏸ 工单填写已暂停</span>
            <button class="resume-btn" @click="onResumeSlot">回到工单填写</button>
          </div>

          <!-- Redirect to manual -->
          <div v-if="msg.slot?.slotStage === 'redirect' || msg.slot?.redirectToManual" class="redirect-banner">
            <span>📝 建议转手工填写</span>
            <button class="redirect-btn" @click="goToManualTicket(msg.slot)">去手工填写工单</button>
          </div>

          <!-- Ticket success -->
          <div v-if="msg.slot?.state === 'TICKET_SUBMIT' && msg.slot.ticketNo" class="ticket-success">
            ✅ 工单已创建：<code>{{ msg.slot.ticketNo }}</code>
          </div>

          <SourceCitation v-if="msg.sources?.length" :sources="msg.sources" />
        </div>
      </div>
    </div>

    <!-- Input area -->
    <div class="input-area">
      <div class="input-row">
        <textarea
          v-model="inputText"
          :maxlength="2000"
          :placeholder="chat.isStreaming ? '回复中...' : '输入您的问题…'"
          :disabled="chat.isStreaming"
          rows="1"
          @keyup.enter.exact="onSend"
        ></textarea>
        <button class="send-btn" :disabled="chat.isStreaming" @click="onSend">
          <span v-if="!chat.isStreaming">➤</span>
          <span v-else style="display:inline-block;animation:spin 1s linear infinite">⟳</span>
        </button>
      </div>
      <div class="quick-actions">
        <button class="quick-btn" :disabled="chat.isStreaming" @click="quickSend('转人工')">👤 转人工</button>
        <button class="quick-btn" :disabled="chat.isStreaming" @click="quickSend('我要投诉')">📮 投诉建议</button>
      </div>
      <div class="input-disclaimer">⚠ 本助手仅基于知识库提供参考，不构成法律意见。低置信度场景将自动转接人工坐席。</div>
    </div>
  </div>
</template>

<style scoped>
.chat-view { display: flex; flex-direction: column; height: 100%; }

/* ===== Messages ===== */
.messages {
  flex: 1; overflow-y: auto; padding: 24px 32px;
  display: flex; flex-direction: column; gap: 18px;
}
.message { display: flex; gap: 12px; max-width: 75%; }
.message.user { align-self: flex-end; flex-direction: row-reverse; }
.message.assistant { align-self: flex-start; }

/* Avatars */
.msg-avatar {
  width: 38px; height: 38px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 600;
}
.message.assistant .msg-avatar { background: var(--color-primary-bg); color: #1d4ed8; }
.message.user .msg-avatar { background: var(--color-border); color: var(--color-text-secondary); }

/* Bubbles */
.bubble {
  padding: 14px 18px; line-height: 1.7; font-size: 14px;
  word-break: break-word; min-width: 0;
}
.message.assistant .bubble {
  background: var(--color-bg-white); color: var(--color-text);
  border-radius: 0 16px 16px 16px;
  box-shadow: var(--shadow-sm); border: 1px solid var(--color-border-light);
}
.message.user .bubble {
  background: var(--color-primary); color: #fff;
  border-radius: 16px 0 16px 16px;
}

.content :deep(p) { margin: 0 0 6px; }
.content :deep(p:last-child) { margin-bottom: 0; }

.chat-top {
  display: flex; align-items: center; gap: 12px; padding: 10px 24px;
  background: var(--color-bg-white); border-bottom: 1px solid var(--color-border); flex-shrink: 0;
}
.new-chat-btn {
  padding: 6px 16px; border-radius: 8px; border: 1px solid var(--color-border);
  background: var(--color-bg-white); color: var(--color-text-secondary);
  font-size: 12px; cursor: pointer; transition: all .15s; font-family: inherit;
}
.new-chat-btn:hover:not(:disabled) { border-color: var(--color-primary-light); color: var(--color-primary); }
.new-chat-btn:disabled { opacity: .5; cursor: not-allowed; }
.human-banner-inline {
  padding: 4px 12px; background: #eff6ff; border-radius: 12px;
  font-size: 12px; color: #1d4ed8; font-weight: 500;
}

/* Complaint type inline buttons */
.complaint-options-inline { margin-top: 10px; display: flex; flex-wrap: wrap; gap: 6px; }
.complaint-opt-btn-inline {
  padding: 6px 12px; border-radius: 8px; border: 1px solid var(--color-border);
  background: var(--color-bg); color: var(--color-text-secondary);
  font-size: 12px; cursor: pointer; transition: all .15s; font-family: inherit;
  line-height: 1.4; text-align: left;
}
.complaint-opt-btn-inline:hover:not(:disabled) {
  border-color: var(--color-primary-light); color: var(--color-primary);
  background: var(--color-primary-bg);
}
.complaint-opt-btn-inline:disabled { opacity: .5; cursor: not-allowed; }

.cursor { animation: blink 1s infinite; color: var(--color-accent); }
@keyframes blink { 50% { opacity: 0; } }

/* ===== Slot UI ===== */
.slot-progress {
  margin-top: 12px; padding: 10px 14px;
  background: var(--color-primary-bg); border-radius: var(--radius);
  border: 1px solid var(--color-primary-bg-hover);
}
.slot-label { font-size: 13px; color: var(--color-primary); font-weight: 500; }

.interrupt-banner {
  margin-top: 12px; padding: 12px 16px;
  background: #fef3c7; border-radius: var(--radius);
  border: 1px solid #fde68a;
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; color: #92400e;
}
.resume-btn {
  background: var(--color-accent); color: #fff; border: none;
  padding: 6px 14px; border-radius: var(--radius-sm); cursor: pointer;
  font-size: 12px; font-weight: 500;
}
.resume-btn:hover { opacity: .85; }

.redirect-banner {
  margin-top: 12px; padding: 12px 16px;
  background: var(--color-primary-bg); border-radius: var(--radius);
  border: 1px solid var(--color-primary-bg-hover);
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; color: var(--color-primary);
}
.redirect-btn {
  background: var(--color-primary); color: #fff; border: none;
  padding: 6px 14px; border-radius: var(--radius-sm); cursor: pointer;
  font-size: 12px; font-weight: 500;
}
.redirect-btn:hover { background: var(--color-primary-hover); }

.ticket-success {
  margin-top: 12px; padding: 10px 14px;
  background: #dcfce7; border-radius: var(--radius);
  border: 1px solid #bbf7d0;
  font-size: 13px; color: #16a34a;
}
.ticket-success code {
  background: #bbf7d0; padding: 2px 8px; border-radius: 4px;
  font-family: monospace; font-weight: 600;
}

/* ===== Input ===== */
.input-area {
  padding: 16px 24px; background: var(--color-bg-white);
  border-top: 1px solid var(--color-border);
}
.quick-actions { display: flex; gap: 8px; margin-top: 8px; justify-content: center; }
.quick-btn {
  padding: 6px 16px; border-radius: 16px; border: 1px solid var(--color-border);
  background: var(--color-bg-white); color: var(--color-text-secondary);
  font-size: 12px; cursor: pointer; transition: all .15s; font-family: inherit;
}
.quick-btn:hover:not(:disabled) { border-color: var(--color-primary-light); color: var(--color-primary); background: var(--color-primary-bg); }
.quick-btn:disabled { opacity: .5; cursor: not-allowed; }
.input-row { display: flex; gap: 12px; align-items: flex-end; }
.input-row textarea {
  flex: 1; padding: 12px 16px; border: 1px solid var(--color-border);
  border-radius: 20px; font-size: 14px; outline: none; resize: none;
  height: 44px; line-height: 1.5; font-family: inherit;
  color: var(--color-text); background: var(--color-bg-white);
  transition: border .15s;
}
.input-row textarea:focus { border-color: var(--color-primary-light); box-shadow: 0 0 0 3px rgba(59,130,246,.08); }
.input-row textarea::placeholder { color: #c0c6d0; }
.input-row textarea:disabled { background: var(--color-bg); cursor: not-allowed; }
.send-btn {
  width: 44px; height: 44px; border-radius: 50%; border: none;
  background: var(--color-primary); color: #fff; font-size: 20px;
  cursor: pointer; transition: all .15s; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.send-btn:hover:not(:disabled) {
  background: var(--color-primary-light);
  transform: scale(1.05);
  box-shadow: 0 4px 14px rgba(37,99,235,.3);
}
.send-btn:disabled { opacity: .6; cursor: not-allowed; }
@keyframes spin { 100% { transform: rotate(360deg); } }

.input-disclaimer { font-size: 11px; color: var(--color-text-muted); text-align: center; margin-top: 8px; }
</style>
