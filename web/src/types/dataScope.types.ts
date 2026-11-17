import type { DataScopeTypeCode } from '@/constants/enums'

/** 数据范围字典。 */
export interface DataScope {
  id: number
  scopeCode: DataScopeTypeCode
  scopeName: string
  description?: string
  sortNo: number
  status: 'ACTIVE' | 'DISABLED'
}

export interface DataScopeCreatePayload {
  scopeCode: string
  scopeName: string
  description?: string
  sortNo?: number
}

/** 角色-数据范围授权。 */
export interface RoleDataScope {
  id: number
  roleId: number
  scopeType: DataScopeTypeCode
  orgId?: number
  projectId?: number
  effectiveTime?: string
  expireTime?: string
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED'
  createdBy?: number
  createdAt: string
}

export interface RoleDataScopeBindPayload {
  scopeType: DataScopeTypeCode
  orgId?: number
  projectId?: number
  expireTime?: string
}

/** 开放 API 资源联合鉴权结果。 */
export interface ResourceDecision {
  functionAllowed: boolean
  dataAllowed: boolean
  allowed: boolean
  matchedScopeIds: number[]
}
