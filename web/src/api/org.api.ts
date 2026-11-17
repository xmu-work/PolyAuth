import { get, postForm, putForm } from '@/utils/request'
import type {
  Org,
  OrgCreatePayload,
  OrgMovePayload,
  OrgUpdatePayload,
} from '@/types/org.types'

export function getOrgTree(rootId?: number): Promise<Org[]> {
  return get<Org[]>('/organizations', rootId === undefined ? undefined : { rootId })
}

export function getOrg(orgId: number): Promise<Org> {
  return get<Org>(`/organizations/${orgId}`)
}

export function getOrgChildren(orgId: number): Promise<Org[]> {
  return get<Org[]>(`/organizations/${orgId}/children`)
}

export function createOrg(payload: OrgCreatePayload): Promise<Org> {
  return postForm<Org>('/organizations', payload)
}

export function moveOrg(orgId: number, payload: OrgMovePayload): Promise<Org> {
  return putForm<Org>(`/organizations/${orgId}/parent`, payload)
}

/** 修改组织名称、排序、负责人和启停状态。 */
export function updateOrg(orgId: number, payload: OrgUpdatePayload): Promise<Org> {
  return putForm<Org>(`/organizations/${orgId}`, payload)
}
