import request from './request'

export interface TicketVO {
  id: number
  ticketNo: string
  sessionId: string
  complaintType: string
  contactName: string
  contactPhone: string
  description: string
  status: string
  priority: string
  assigneeId: number | null
  assignedAt: string | null
  handledAt: string | null
  handlerNotes: string | null
  createTime: string
  updateTime: string
}

/** 待处理工单池 */
export function listPending(): Promise<TicketVO[]> {
  return request.get('/operator/tickets/pending')
}

/** 我处理中的工单 */
export function listMyProcessing(): Promise<TicketVO[]> {
  return request.get('/operator/tickets/processing')
}

/** 我所有工单（不限状态） */
export function listMyAll(): Promise<TicketVO[]> {
  return request.get('/operator/tickets/my-all')
}

/** 全量工单 */
export function listAllTickets(): Promise<TicketVO[]> {
  return request.get('/operator/tickets/all')
}

/** 工单详情 */
export function getTicket(ticketNo: string): Promise<TicketVO> {
  return request.get(`/operator/tickets/${ticketNo}`)
}

/** 认领工单 */
export function claimTicket(ticketNo: string): Promise<TicketVO> {
  return request.post(`/operator/tickets/${ticketNo}/claim`)
}

/** 提交处理 */
export function processTicket(ticketNo: string, handlerNotes: string, targetStatus?: string): Promise<TicketVO> {
  return request.put(`/operator/tickets/${ticketNo}`, { handlerNotes, targetStatus })
}

// ==================== 人工会话 ====================

export interface HandoffSummary {
  userProfile: string
  intent: string
  confidence: number
  coreQuestion: string
  retrievedDocs: string
  aiResponseSummary: string
  transferReason: string
  suggestedVerification: string
  stats: string
}

export interface WaitingSessionVO {
  sessionId: string
  lastMessage: string
  transferReason: string
  createTime: string
  summary: HandoffSummary | null
}

/** 等待人工接入的会话列表 */
export function listWaitingSessions(): Promise<WaitingSessionVO[]> {
  return request.get('/operator/sessions/waiting')
}

// ==================== 坐席对话 ====================

export interface ContextMessage {
  role: string
  content: string
}

/** 获取会话消息 */
export function getSessionMessages(sessionId: string, since?: number): Promise<ContextMessage[]> {
  return request.get(`/operator/sessions/${sessionId}/messages`, { params: { since: since ?? 0 } })
}

/** 坐席发送消息 */
export function sendSessionMessage(sessionId: string, content: string): Promise<void> {
  return request.post(`/operator/sessions/${sessionId}/message`, { content })
}

/** 坐席接管会话 */
export function takeoverSession(sessionId: string): Promise<{ sessionId: string; history: ContextMessage[] }> {
  return request.post(`/operator/sessions/${sessionId}/takeover`)
}

/** 我的活跃会话列表 */
export function listMySessions(): Promise<WaitingSessionVO[]> {
  return request.get('/operator/sessions/my')
}

/** 坐席关闭会话 */
export function closeSession(sessionId: string, notes: string): Promise<void> {
  return request.post(`/operator/sessions/${sessionId}/close`, { notes })
}
