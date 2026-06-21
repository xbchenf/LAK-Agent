export interface DocumentVO {
  docId: string
  title: string
  docType: 'POLICY' | 'PROCEDURE'
  status: 'DRAFT' | 'ACTIVE' | 'EXPIRED'
  fileUrl: string
  fileSize: number | null
  effectiveDate: string | null
  expireDate: string | null
  chunkCount: number
  qdrantCollection: string
  createTime: string
  updateTime: string
}

export interface DocumentQueryDTO {
  docType?: string
  status?: string
  keyword?: string
  page?: number
  size?: number
}

export interface DocumentChunkVO {
  chunkIndex: number
  text: string
  textLength: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export type StatusAction = 'publish' | 'disable' | 'reactivate'

export const DocTypeLabels: Record<string, string> = {
  POLICY: '政策',
  PROCEDURE: '办事指引',

}

export const StatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '已发布',
  EXPIRED: '已过期',
}
