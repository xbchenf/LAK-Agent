import request from './request'
import type { AuditLogVO, AuditLogQuery } from '@/types/audit'
import type { PageResult } from '@/types'

export function listAuditLogs(params: AuditLogQuery): Promise<PageResult<AuditLogVO>> {
  return request.get('/admin/audit-logs', { params }) as any
}

export function getAuditLog(id: number, month?: string): Promise<AuditLogVO> {
  return request.get(`/admin/audit-logs/${id}`, { params: { month } }) as any
}
