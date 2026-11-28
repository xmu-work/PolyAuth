import { computed, ref } from 'vue'
import { listRoles, listUserRoles } from '@/api/role.api'
import { getRolePermissions, listPermissions, listSystems } from '@/api/system.api'
import {
  CommonStatus,
  GrantStatus,
  OperationAction,
  RoleType,
  type OperationActionCode,
} from '@/constants/enums'
import type { Role, UserRole } from '@/types/role.types'
import type { Permission, RolePermission, System } from '@/types/system.types'

/** 生效角色：直接授予或经父角色继承而来（契约：角色继承链参与权限判定）。 */
export interface EffectiveRole {
  id: number
  roleName: string
  roleCode: string
  roleType: Role['roleType']
  /** true=直接授予；false=通过父角色继承 */
  direct: boolean
  /** 直接授予时保留授权记录，用于展示有效期 */
  grant: UserRole | null
}

/** 合并后的生效权限：同一权限被多个角色授予时取最宽松的动作。 */
export interface EffectivePermission {
  permissionId: number
  permissionCode: string
  permissionName: string
  permissionType: Permission['permissionType']
  /** 权限节点自身要求的动作 */
  requiredAction: OperationActionCode
  /** 角色实际授予的动作 */
  allowedAction: OperationActionCode
  /** 授予该权限的角色名称（含继承角色） */
  sourceRoles: string[]
}

/** 按子系统分组的权限集合。 */
export interface PermissionGroup {
  systemId: number
  systemCode: string
  systemName: string
  permissions: EffectivePermission[]
}

/** 与后端 OperationAction.covers 保持一致的基础能力映射。 */
const ACTION_CAPABILITIES: Record<OperationActionCode, ReadonlySet<string>> = {
  [OperationAction.NONE]: new Set(),
  [OperationAction.VIEW]: new Set(['VIEW']),
  [OperationAction.ALL]: new Set(['VIEW', 'ADD', 'EDIT', 'APPROVE', 'EXPORT']),
  [OperationAction.VIEW_EDIT]: new Set(['VIEW', 'EDIT']),
  [OperationAction.VIEW_ADD]: new Set(['VIEW', 'ADD']),
  [OperationAction.VIEW_APPROVE]: new Set(['VIEW', 'APPROVE']),
  [OperationAction.VIEW_EXPORT]: new Set(['VIEW', 'EXPORT']),
  [OperationAction.VIEW_EDIT_APPROVE]: new Set(['VIEW', 'EDIT', 'APPROVE']),
  [OperationAction.VIEW_ADD_EDIT]: new Set(['VIEW', 'ADD', 'EDIT']),
  [OperationAction.VIEW_EDIT_EXPORT]: new Set(['VIEW', 'EDIT', 'EXPORT']),
  [OperationAction.VIEW_SELF]: new Set(['VIEW']),
  [OperationAction.VIEW_EDIT_SELF]: new Set(['VIEW', 'EDIT']),
}

function covers(allowed: OperationActionCode, required: OperationActionCode): boolean {
  const granted = ACTION_CAPABILITIES[allowed]
  const needed = ACTION_CAPABILITIES[required]
  if (!granted || !needed) {
    return false
  }
  return needed.size > 0 && [...needed].every((capability) => granted.has(capability))
}

function capabilityCount(action: OperationActionCode): number {
  return ACTION_CAPABILITIES[action]?.size ?? 0
}

function isEffectiveRole(role: Role | undefined): role is Role {
  return (
    role !== undefined &&
    role.isDeleted === 0 &&
    role.roleType === RoleType.FUNC &&
    role.status === CommonStatus.ACTIVE
  )
}

/**
 * 计算用户当前生效的角色：直接授权必须处于生效期内，父角色链按后端规则逐级继承。
 * 与 DecisionService#activeRoleIds 语义对齐，停用/删除的角色会截断继承链。
 */
export function resolveEffectiveRoles(
  grants: UserRole[],
  roles: Role[],
  now: Date = new Date(),
): EffectiveRole[] {
  const roleMap = new Map(roles.map((role) => [role.id, role]))
  const nowMs = now.getTime()

  const directGrants = new Map<number, UserRole>()
  for (const grant of grants) {
    if (grant.status !== GrantStatus.ACTIVE) {
      continue
    }
    if (grant.effectiveTime && new Date(grant.effectiveTime).getTime() > nowMs) {
      continue
    }
    if (grant.expireTime && new Date(grant.expireTime).getTime() <= nowMs) {
      continue
    }
    if (!directGrants.has(grant.roleId)) {
      directGrants.set(grant.roleId, grant)
    }
  }

  const result: EffectiveRole[] = []
  const visited = new Set<number>()

  for (const [roleId, grant] of directGrants) {
    const role = roleMap.get(roleId)
    if (!isEffectiveRole(role) || visited.has(roleId)) {
      continue
    }
    visited.add(roleId)
    result.push({
      id: role.id,
      roleName: role.roleName,
      roleCode: role.roleCode ?? '',
      roleType: role.roleType,
      direct: true,
      grant,
    })
  }

  // 继承链：从直接角色的父角色开始逐级上溯，已访问节点去重以防御成环。
  let cursor = result
    .map((role) => roleMap.get(role.id)?.parentId ?? null)
    .filter((parentId): parentId is number => parentId !== null)
  while (cursor.length > 0) {
    const next: number[] = []
    for (const parentId of cursor) {
      if (visited.has(parentId)) {
        continue
      }
      const parent = roleMap.get(parentId)
      // 父角色不可用时不登记该节点，同时截断其更上层继承，与后端判定一致。
      if (!isEffectiveRole(parent)) {
        continue
      }
      visited.add(parentId)
      result.push({
        id: parent.id,
        roleName: parent.roleName,
        roleCode: parent.roleCode ?? '',
        roleType: parent.roleType,
        direct: false,
        grant: null,
      })
      if (parent.parentId != null) {
        next.push(parent.parentId)
      }
    }
    cursor = next
  }

  return result
}

/**
 * 聚合角色权限：同一权限被多个角色授予时合并来源角色，并取最宽松的授权动作。
 * 仅保留启用中的系统与权限节点，且授权动作必须覆盖权限节点要求的动作。
 */
export function buildPermissionGroups(
  effectiveRoles: EffectiveRole[],
  rolePermissionLists: RolePermission[][],
  systems: System[],
  permissionLists: Permission[][],
): PermissionGroup[] {
  interface Aggregate {
    permission: Permission
    system: System
    allowedAction: OperationActionCode
    sourceRoles: string[]
    sourceRoleIds: Set<number>
  }

  const permissionIndex = new Map<number, { permission: Permission; system: System }>()
  systems.forEach((system, index) => {
    for (const permission of permissionLists[index] ?? []) {
      if (permission.isDeleted !== 0 || permission.status !== CommonStatus.ACTIVE) {
        continue
      }
      permissionIndex.set(permission.id, { permission, system })
    }
  })

  const aggregated = new Map<number, Aggregate>()
  effectiveRoles.forEach((role, index) => {
    for (const binding of rolePermissionLists[index] ?? []) {
      const indexed = permissionIndex.get(binding.permissionId)
      if (!indexed) {
        continue
      }
      const allowedAction = binding.allowedAction
      if (
        allowedAction === OperationAction.NONE ||
        !covers(allowedAction, indexed.permission.action)
      ) {
        continue
      }
      const existing = aggregated.get(binding.permissionId)
      if (!existing) {
        aggregated.set(binding.permissionId, {
          permission: indexed.permission,
          system: indexed.system,
          allowedAction,
          sourceRoles: [role.roleName],
          sourceRoleIds: new Set([role.id]),
        })
        continue
      }
      if (capabilityCount(allowedAction) > capabilityCount(existing.allowedAction)) {
        existing.allowedAction = allowedAction
      }
      if (!existing.sourceRoleIds.has(role.id)) {
        existing.sourceRoleIds.add(role.id)
        existing.sourceRoles.push(role.roleName)
      }
    }
  })

  const groupMap = new Map<number, PermissionGroup>()
  for (const item of aggregated.values()) {
    const group = groupMap.get(item.system.id) ?? {
      systemId: item.system.id,
      systemCode: item.system.systemCode,
      systemName: item.system.systemName,
      permissions: [],
    }
    group.permissions.push({
      permissionId: item.permission.id,
      permissionCode: item.permission.permissionCode,
      permissionName: item.permission.permissionName,
      permissionType: item.permission.permissionType,
      requiredAction: item.permission.action,
      allowedAction: item.allowedAction,
      sourceRoles: item.sourceRoles,
    })
    groupMap.set(item.system.id, group)
  }

  return systems
    .map((system) => groupMap.get(system.id))
    .filter((group): group is PermissionGroup => group !== undefined)
    .map((group) => ({
      ...group,
      permissions: group.permissions.sort(
        (left, right) =>
          permissionTypeOrder(left.permissionType) - permissionTypeOrder(right.permissionType) ||
          left.permissionId - right.permissionId,
      ),
    }))
}

function permissionTypeOrder(type: Permission['permissionType']): number {
  return type === 'MENU' ? 0 : 1
}

/** 用户详情「角色与权限」数据源：角色授权 + 继承链 + 角色权限聚合。 */
export function useUserAuthorization() {
  const roles = ref<EffectiveRole[]>([])
  const groups = ref<PermissionGroup[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  let requestVersion = 0

  const totalPermissions = computed(() =>
    groups.value.reduce((total, group) => total + group.permissions.length, 0),
  )

  function reset(): void {
    requestVersion += 1
    roles.value = []
    groups.value = []
    loading.value = false
    error.value = null
  }

  async function load(userId: number): Promise<void> {
    const version = ++requestVersion
    roles.value = []
    groups.value = []
    loading.value = true
    error.value = null
    try {
      const [grants, roleList] = await Promise.all([listUserRoles(userId), listRoles()])
      if (version !== requestVersion) {
        return
      }
      const effectiveRoles = resolveEffectiveRoles(grants, roleList)
      roles.value = effectiveRoles
      if (effectiveRoles.length === 0) {
        return
      }

      const [systemList, rolePermissionLists] = await Promise.all([
        listSystems(),
        Promise.all(effectiveRoles.map((role) => getRolePermissions(role.id))),
      ])
      if (version !== requestVersion) {
        return
      }
      const activeSystems = systemList.filter(
        (system) => system.status === CommonStatus.ACTIVE && system.isDeleted === 0,
      )
      const permissionLists = await Promise.all(
        activeSystems.map((system) => listPermissions(system.id)),
      )
      if (version !== requestVersion) {
        return
      }
      groups.value = buildPermissionGroups(
        effectiveRoles,
        rolePermissionLists,
        activeSystems,
        permissionLists,
      )
    } catch (caught) {
      if (version !== requestVersion) {
        return
      }
      roles.value = []
      groups.value = []
      error.value = caught instanceof Error ? caught.message : '角色与权限加载失败'
    } finally {
      if (version === requestVersion) {
        loading.value = false
      }
    }
  }

  return { roles, groups, loading, error, totalPermissions, load, reset }
}
