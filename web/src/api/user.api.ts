import { get, postForm, putForm } from '@/utils/request'
import type { PageResult } from '@/types/common'
import type {
  User,
  UserCreatePayload,
  UserListQuery,
  UserStatusPayload,
  UserTransferPayload,
  SensitiveUser,
} from '@/types/user.types'

export function listUsers(query: UserListQuery): Promise<PageResult<User>> {
  return get<PageResult<User>>('/users', query)
}

export function getUser(userId: number): Promise<User> {
  return get<User>(`/users/${userId}`)
}

export function createUser(payload: UserCreatePayload): Promise<User> {
  return postForm<User>('/users', payload)
}

export function changeUserStatus(userId: number, payload: UserStatusPayload): Promise<User> {
  return putForm<User>(`/users/${userId}/status`, payload)
}

/** 调整员工组织归属与岗位，不自动删除已有角色。 */
export function transferUser(userId: number, payload: UserTransferPayload): Promise<User> {
  return putForm<User>(`/users/${userId}/organization`, payload)
}

/** 通过独立权限查看姓名与电话明文；后端统一审计该请求。 */
export function getSensitiveUser(userId: number): Promise<SensitiveUser> {
  return get<SensitiveUser>(`/users/${userId}/sensitive`)
}
