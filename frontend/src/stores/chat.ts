import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SourceDoc } from '@/types'

export interface SlotState {
  state?: string           // COLLECT_INFO | TICKET_SUBMIT | CLOSED
  slotStage?: string       // started | fill | modify | interrupted | redirect | done
  interruptReason?: string
  ticketNo?: string
  redirectToManual?: boolean
  prefilledSlots?: Record<string, string>
}

export interface UIMessage {
  role: 'user' | 'assistant'
  content: string
  sources?: SourceDoc[]
  isStreaming?: boolean
  sessionId?: string
  slot?: SlotState          // 槽位填充状态（仅 assistant 消息有值）
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<UIMessage[]>([])
  const currentSessionId = ref<string | null>(null)
  const isStreaming = ref(false)

  const lastMessage = computed(() => messages.value.at(-1) ?? null)

  function addMessage(msg: UIMessage) {
    messages.value.push(msg)
  }

  function updateLastAssistant(content: string, sources?: SourceDoc[]) {
    const last = messages.value.at(-1)
    if (last && last.role === 'assistant') {
      last.content = content
      last.isStreaming = true
      if (sources) last.sources = sources
    } else {
      messages.value.push({ role: 'assistant', content, sources, isStreaming: true })
    }
  }

  function finishStreaming(sources?: SourceDoc[], sessionId?: string, slot?: SlotState) {
    const last = messages.value.at(-1)
    if (last && last.role === 'assistant') {
      last.isStreaming = false
      if (sources) last.sources = sources
      if (sessionId) {
        last.sessionId = sessionId
        currentSessionId.value = sessionId
      }
      if (slot) last.slot = slot
    }
  }

  function setSlotState(slot: SlotState) {
    const last = messages.value.at(-1)
    if (last && last.role === 'assistant') {
      last.slot = slot
    }
  }

  /** 检查当前是否在槽位填充中（最近一条 assistant 消息的 slot.state === 'COLLECT_INFO'） */
  function isInSlotFilling(): boolean {
    const last = messages.value.at(-1)
    return last?.slot?.state === 'COLLECT_INFO' && last.slot.slotStage !== 'interrupted'
  }

  function clearMessages() {
    messages.value = []
    currentSessionId.value = null
  }

  return { messages, currentSessionId, isStreaming, lastMessage, addMessage, updateLastAssistant, finishStreaming, setSlotState, isInSlotFilling, clearMessages }
})
