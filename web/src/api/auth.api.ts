import { get, postForm, postJson, putForm } from '@/utils/request'
import type { ChangePasswordPayload, LoginPayload, LoginResponse } from '@/types/auth.types'

export function login(payload: LoginPayload): Promise<LoginResponse> {
  return postJson<LoginResponse>('/auth/login', payload)
}

export function logout(): Promise<void> {
  return postForm<void>('/auth/logout')
}

export function validateSession(): Promise<boolean> {
  return get<boolean>('/auth/validate')
}

/** 校验当前用户是否具有指定前端权限码；菜单切换时调用。 */
export async function checkPermission(permissionCode: string): Promise<boolean> {
  const result = await get<{ allowed: boolean }>(
    `/auth/permissions/${encodeURIComponent(permissionCode)}`,
  )
  return result.allowed
}

export function changePassword(payload: ChangePasswordPayload): Promise<void> {
  return putForm<void>('/auth/password', payload)
}
