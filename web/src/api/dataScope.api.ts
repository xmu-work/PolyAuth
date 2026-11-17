import { deleteForm, get, postForm } from '@/utils/request'
import type {
  DataScope,
  DataScopeCreatePayload,
  RoleDataScope,
  RoleDataScopeBindPayload,
} from '@/types/dataScope.types'

/** 查询数据范围字典。 */
export function listDataScopes(): Promise<DataScope[]> {
  return get<DataScope[]>('/systems/data-scopes')
}

/** 创建数据范围字典。 */
export function createDataScope(payload: DataScopeCreatePayload): Promise<DataScope> {
  return postForm<DataScope>('/systems/data-scopes', payload)
}

/** 查询角色的数据范围授权。 */
export function listRoleDataScopes(roleId: number): Promise<RoleDataScope[]> {
  return get<RoleDataScope[]>(`/roles/${roleId}/data-scopes`)
}

/** 为角色绑定数据范围。 */
export function bindRoleDataScope(
  roleId: number,
  payload: RoleDataScopeBindPayload,
): Promise<RoleDataScope> {
  return postForm<RoleDataScope>(`/roles/${roleId}/data-scopes`, payload)
}

/** 回收角色数据范围。 */
export function revokeRoleDataScope(roleId: number, bindingId: number): Promise<void> {
  return deleteForm<void>(`/roles/${roleId}/data-scopes/${bindingId}`)
}
