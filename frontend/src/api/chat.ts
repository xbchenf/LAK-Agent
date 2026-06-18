import request from './request'
import type { ChatMessageVO, PageResult } from '@/types'

export interface SessionVO {
  sessionId: string
  status: string
  intentType?: string
  createTime: string
}

export function sendMessage(sessionId: string | null, message: string): Promise<ChatMessageVO> {
  return request.post('/chat/message', { sessionId, message }) as any
}

export function listSessions(params: { page: number; size: number }): Promise<PageResult<SessionVO>> {
  return request.get('/chat/sessions', { params }) as any
}

export function getSession(sessionId: string): Promise<SessionVO> {
  return request.get(`/chat/sessions/${sessionId}`) as any
}

export function deleteSession(sessionId: string): Promise<void> {
  return request.delete(`/chat/sessions/${sessionId}`) as any
}
