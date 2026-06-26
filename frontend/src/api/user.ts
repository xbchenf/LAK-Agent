import request from './request'
import type { UserVO } from '@/types/user'

export function listUsers(params?: { username?: string; status?: string }): Promise<UserVO[]> {
  return request.get('/admin/users', { params }) as any
}

export function getUser(id: number): Promise<UserVO> {
  return request.get(`/admin/users/${id}`) as any
}

export function createUser(data: { username: string; password: string; realName?: string; roleId?: number }): Promise<UserVO> {
  return request.post('/admin/users', data) as any
}

export function updateUser(id: number, data: Record<string, any>): Promise<UserVO> {
  return request.put(`/admin/users/${id}`, data) as any
}

export function resetPassword(id: number, password: string): Promise<void> {
  return request.put(`/admin/users/${id}/password`, { password }) as any
}
