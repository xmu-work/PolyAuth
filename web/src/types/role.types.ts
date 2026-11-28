import type { CommonStatusCode, GrantStatusCode, RoleTypeCode } from '@/constants/enums'

export interface Role {
  id: number
  /** 内部唯一编码，仅后端使用，界面不展示 */
  roleCode?: string
  roleName: string
  roleType: RoleTypeCode
  /** 兼容单父调用方；多继承以 parentIds 为准 */
  parentId?: number
  parentIds?: number[]
  status: CommonStatusCode
  description?: string
  isDeleted: number
  createdAt: string
  updatedAt: string
  createdBy?: number
  updatedBy?: number
}

export interface RoleCreatePayload {
  roleName: string
  roleType: RoleTypeCode
  description?: string
  parentId?: number
  parentIds?: number[]
}

export interface RoleUpdatePayload {
  roleName?: string
  description?: string
  status?: CommonStatusCode
  parentId?: number
  parentIds?: number[]
}

export interface UserRole {
  id: number
  userId: number
  roleId: number
  effectiveTime?: string
  expireTime?: string
  grantBy?: number
  grantReason?: string
  status: GrantStatusCode
  createdAt: string
  updatedAt: string
}

export interface GrantPayload {
  userId: number
  roleId: number
  expireTime?: string
  grantBy?: number
  grantReason?: string
}
