import request from './request'
import type { RoleVO, MenuVO } from '@/types/role'

export function listRoles(): Promise<RoleVO[]> {
  return request.get('/admin/roles') as any
}

export function listMenus(): Promise<MenuVO[]> {
  return request.get('/admin/menus') as any
}

export function updateRoleMenus(roleId: number, menuIds: number[]): Promise<void> {
  return request.put(`/admin/roles/${roleId}/menus`, { menuIds }) as any
}

export function getMyMenus(): Promise<string[]> {
  return request.get('/admin/menus/me') as any
}
