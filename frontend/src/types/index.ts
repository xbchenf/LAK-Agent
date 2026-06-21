export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PageQuery {
  page: number
  size: number
}

export interface LoginVO {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: number
  username: string
  realName: string
  roles: string[]
}

export interface SourceDoc {
  docId: string
  title: string
  sourceNo: string
  articleNo?: string
  chapter?: string
  effectiveDate?: string
  fragment: string
}

export interface ChatMessageVO {
  sessionId: string
  role: 'user' | 'assistant'
  answer?: string
  content?: string
  sources?: SourceDoc[]
  intentType?: string
  confidence?: number
}
