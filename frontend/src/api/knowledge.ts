import request from './request'
import type { DocumentVO, DocumentQueryDTO, DocumentChunkVO, PageResult, StatusAction } from '@/types/knowledge'

export function uploadDocument(formData: FormData): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.post('/knowledge/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function listDocuments(params: DocumentQueryDTO): Promise<{ code: number; message: string; data: PageResult<DocumentVO> }> {
  return request.get('/knowledge/documents', { params })
}

export function getDocument(docId: string): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.get(`/knowledge/documents/${docId}`)
}

export function updateDocument(
  docId: string,
  data: { title?: string; effectiveDate?: string; expireDate?: string }
): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.put(`/knowledge/documents/${docId}`, null, { params: data })
}

export function changeStatus(docId: string, action: StatusAction): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.patch(`/knowledge/documents/${docId}/status`, { action })
}

export function deleteDocument(docId: string): Promise<{ code: number; message: string }> {
  return request.delete(`/knowledge/documents/${docId}`)
}

export function getChunks(docId: string): Promise<{ code: number; message: string; data: DocumentChunkVO[] }> {
  return request.get(`/knowledge/documents/${docId}/chunks`)
}

export function reindexDocument(docId: string): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.post(`/knowledge/documents/${docId}/reindex`)
}
