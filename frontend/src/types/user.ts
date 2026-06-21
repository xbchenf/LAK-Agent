export interface UserVO {
  id: number
  username: string
  realName: string
  email: string
  phone: string
  status: string
  roleId: number
  roleName: string
  createTime: string
}

export const StatusLabels: Record<string, string> = {
  ACTIVE: '正常',
  DISABLED: '禁用',
  LOCKED: '锁定',
}
