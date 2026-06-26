<script setup lang="ts">
import { ref, nextTick, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import type { SourceDoc } from '@/types'
import type { SlotState } from '@/stores/chat'
import SourceCitation from '@/components/chat/SourceCitation.vue'

const router = useRouter()

const chat = useChatStore()
const auth = useAuthStore()
const inputText = ref('')
const messagesEl = ref<HTMLElement>()

// 槽位标签
const slotLabels: Record<string, string> = {
  complaintType: '投诉类型', contactName: '联系人', contactPhone: '手机号',
  description: '问题描述', attachment: '附件',
}

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
              // 解析槽位状态（来自 extra 或顶层字段）
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

/** 用户点击"回到工单" — 清除中断状态，让用户看到当前槽位提示继续填写 */
function onResumeSlot() {
  // 清除最近一条 assistant 消息的 interrupted 状态
  const last = chat.messages.at(-1)
  if (last?.slot?.slotStage === 'interrupted') {
    last.slot = { state: 'COLLECT_INFO', slotStage: 'fill' }
  }
  scrollBottom()
}

/** 转手工填写 — 携带已填数据跳转到 /tickets */
function goToManualTicket(slot?: SlotState | null) {
  const prefill = (slot as any)?.prefilledSlots as Record<string, string> | undefined
  if (prefill && Object.keys(prefill).length > 0) {
    sessionStorage.setItem('ticket_prefill', JSON.stringify(prefill))
  }
  router.push('/tickets')
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

          <!-- 槽位填充进度条 -->
          <div v-if="msg.slot?.state === 'COLLECT_INFO' && msg.slot.slotStage !== 'interrupted'" class="slot-progress">
            <div class="slot-label">📋 工单填写中</div>
          </div>

          <!-- 中断提示 + 回到工单按钮 -->
          <div v-if="msg.slot?.state === 'COLLECT_INFO' && msg.slot.slotStage === 'interrupted'" class="interrupt-banner">
            <span>⏸ 工单填写已暂停</span>
            <button class="resume-btn" @click="onResumeSlot">回到工单填写</button>
          </div>

          <!-- 引导转手工填写 -->
          <div v-if="msg.slot?.slotStage === 'redirect' || msg.slot?.redirectToManual" class="redirect-banner">
            <span>📝 建议转手工填写</span>
            <button class="redirect-btn" @click="goToManualTicket(msg.slot)">去手工填写工单</button>
          </div>

          <!-- 工单创建成功 -->
          <div v-if="msg.slot?.state === 'TICKET_SUBMIT' && msg.slot.ticketNo" class="ticket-success">
            ✅ 工单已创建：<code>{{ msg.slot.ticketNo }}</code>
          </div>

          <SourceCitation v-if="msg.sources?.length" :sources="msg.sources" />
        </div>
      </div>
    </div>

    <div class="input-area">
      <el-input
        v-model="inputText"
        :maxlength="2000"
        :placeholder="chat.isStreaming ? '回复中...' : '输入您的问题...'"
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

/* 槽位进度条 */
.slot-progress {
  margin-top: 12px; padding: 10px 14px;
  background: linear-gradient(135deg, #e8f4fd 0%, #f0f7ff 100%);
  border-radius: 10px; border: 1px solid #cce5ff;
}
.slot-label { font-size: 13px; color: var(--color-primary); font-weight: 500; }

/* 中断提示 */
.interrupt-banner {
  margin-top: 12px; padding: 12px 16px;
  background: #fff8e1; border-radius: 10px;
  border: 1px solid #ffe082;
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; color: #795548;
}
.resume-btn {
  background: var(--color-accent); color: #fff; border: none;
  padding: 6px 14px; border-radius: 6px; cursor: pointer;
  font-size: 12px; font-weight: 500;
}
.resume-btn:hover { opacity: 0.85; }

/* 引导转手工 */
.redirect-banner {
  margin-top: 12px; padding: 12px 16px;
  background: #e3f2fd; border-radius: 10px;
  border: 1px solid #90caf9;
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; color: #1565c0;
}
.redirect-btn {
  background: #1565c0; color: #fff; border: none;
  padding: 6px 14px; border-radius: 6px; cursor: pointer;
  font-size: 12px; font-weight: 500;
}
.redirect-btn:hover { opacity: 0.85; }

/* 工单成功 */
.ticket-success {
  margin-top: 12px; padding: 10px 14px;
  background: #e8f5e9; border-radius: 10px;
  border: 1px solid #a5d6a7;
  font-size: 13px; color: #2e7d32;
}
.ticket-success code {
  background: #c8e6c9; padding: 2px 8px; border-radius: 4px;
  font-family: monospace; font-weight: 600;
}

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
