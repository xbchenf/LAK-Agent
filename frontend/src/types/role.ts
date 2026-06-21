export interface RoleVO {
  id: number
  roleCode: string
  roleName: string
  description: string
  menuIds: number[]
}

export interface MenuVO {
  id: number
  menuCode: string
  menuName: string
  children?: MenuVO[]
}
