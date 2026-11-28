import { deleteForm, get, postForm, putJson } from '@/utils/request'
import type {
  GrantPayload,
  Role,
  RoleCreatePayload,
  RoleUpdatePayload,
  UserRole,
} from '@/types/role.types'

export function listRoles(): Promise<Role[]> {
  return get<Role[]>('/roles')
}

export function getRole(roleId: number): Promise<Role> {
  return get<Role>(`/roles/${roleId}`)
}

export function createRole(payload: RoleCreatePayload): Promise<Role> {
  const { parentIds, ...rest } = payload
  return postForm<Role>('/roles', {
    ...rest,
    // 兼容只认识单父角色的旧后端；新后端优先使用 parentIds
    parentId: rest.parentId ?? parentIds?.[0],
    // Spring 的 List<Long> 可直接绑定逗号分隔的查询参数
    parentIds: parentIds && parentIds.length > 0 ? parentIds.join(',') : undefined,
  })
}

/** 修改角色名称、描述、状态和多个父角色。 */
export function updateRole(roleId: number, payload: RoleUpdatePayload): Promise<Role> {
  const { parentIds, ...rest } = payload
  return putJson<Role>(`/roles/${roleId}`, {
    ...rest,
    // 兼容旧后端；新后端优先使用 parentIds
    parentId: rest.parentId ?? parentIds?.[0],
    parentIds,
  })
}

/** 查询角色互斥关系中的对端角色 ID。 */
export function listRoleExclusions(roleId: number): Promise<number[]> {
  return get<number[]>(`/roles/${roleId}/exclusions`)
}

/** 新建角色互斥关系。 */
export function bindRoleExclusion(roleId: number, excludedRoleId: number): Promise<void> {
  return postForm<void>(`/roles/${roleId}/exclusions`, { excludedRoleId })
}

/** 删除角色互斥关系。 */
export function unbindRoleExclusion(roleId: number, excludedRoleId: number): Promise<void> {
  return deleteForm<void>(`/roles/${roleId}/exclusions/${excludedRoleId}`)
}

export function grantRole(payload: GrantPayload): Promise<UserRole> {
  return postForm<UserRole>(`/roles/${payload.roleId}/assignments`, {
    userId: payload.userId,
    expireTime: payload.expireTime,
    grantReason: payload.grantReason,
  })
}

export function revokeRole(userId: number, roleId: number): Promise<void> {
  return deleteForm<void>(`/roles/${roleId}/assignments/${userId}`)
}

export function listUserRoles(userId: number): Promise<UserRole[]> {
  return get<UserRole[]>('/roles/assignments', { userId })
}

export function listRoleAssignments(roleId: number): Promise<UserRole[]> {
  return get<UserRole[]>(`/roles/${roleId}/assignments`)
}
