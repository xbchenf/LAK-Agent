import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SourceDoc } from '@/types'

export interface UIMessage {
  role: 'user' | 'assistant'
  content: string
  sources?: SourceDoc[]
  isStreaming?: boolean
  sessionId?: string
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

  function finishStreaming(sources?: SourceDoc[], sessionId?: string) {
    const last = messages.value.at(-1)
    if (last && last.role === 'assistant') {
      last.isStreaming = false
      if (sources) last.sources = sources
      if (sessionId) {
        last.sessionId = sessionId
        currentSessionId.value = sessionId
      }
    }
  }

  function clearMessages() {
    messages.value = []
    currentSessionId.value = null
  }

  return { messages, currentSessionId, isStreaming, lastMessage, addMessage, updateLastAssistant, finishStreaming, clearMessages }
})
