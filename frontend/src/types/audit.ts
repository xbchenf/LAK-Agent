export interface AuditLogVO {
  id: number
  traceId: string
  sessionId: string | null
  userId: number | null
  username: string | null
  requestUri: string | null
  operation: string | null
  requestBody: string | null
  responseBody: string | null
  intentType: string | null
  confidence: number | null
  modelParams: string | null
  modelResponse: string | null
  retrievalFragments: string | null
  latencyMs: number | null
  status: string
  errorMessage: string | null
  createTime: string
}

export interface AuditLogQuery {
  userId?: number
  status?: string
  keyword?: string
  startDate?: string
  endDate?: string
  month?: string
  page?: number
  size?: number
}

export const StatusLabels: Record<string, string> = {
  SUCCESS: '成功',
  FAIL: '失败',
  FALLBACK: '降级',
}

export const StatusColors: Record<string, string> = {
  SUCCESS: 'success',
  FAIL: 'danger',
  FALLBACK: 'warning',
}
