import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  resolveEffectiveRoles,
  useUserAuthorization,
} from '@/composables/useUserAuthorization'
import { listRoles, listUserRoles } from '@/api/role.api'
import { getRolePermissions, listPermissions, listSystems } from '@/api/system.api'
import type { Role, UserRole } from '@/types/role.types'
import type { Permission, RolePermission, System } from '@/types/system.types'

vi.mock('@/api/role.api', () => ({
  listRoles: vi.fn(),
  listUserRoles: vi.fn(),
}))

vi.mock('@/api/system.api', () => ({
  getRolePermissions: vi.fn(),
  listPermissions: vi.fn(),
  listSystems: vi.fn(),
}))

const NOW = new Date('2026-11-25T10:00:00+08:00')

function makeRole(overrides: Partial<Role> & Pick<Role, 'id' | 'roleName'>): Role {
  return {
    roleCode: `ROLE_${overrides.id}`,
    roleType: 'FUNC',
    status: 'ACTIVE',
    isDeleted: 0,
    createdAt: '2026-11-01T10:00:00',
    updatedAt: '2026-11-01T10:00:00',
    ...overrides,
  }
}

function makeGrant(overrides: Partial<UserRole> & Pick<UserRole, 'id' | 'roleId'>): UserRole {
  return {
    userId: 16,
    effectiveTime: '2026-11-01T10:00:00+08:00',
    expireTime: '2027-11-01T10:00:00+08:00',
    status: 'ACTIVE',
    createdAt: '2026-11-01T10:00:00',
    updatedAt: '2026-11-01T10:00:00',
    ...overrides,
  }
}

function makePermission(
  overrides: Partial<Permission> & Pick<Permission, 'id' | 'permissionName' | 'action'>,
): Permission {
  return {
    systemId: 1,
    permissionCode: `PERM_${overrides.id}`,
    permissionType: 'BUTTON',
    sortNo: 1,
    status: 'ACTIVE',
    isDeleted: 0,
    createdAt: '2026-11-01T10:00:00',
    updatedAt: '2026-11-01T10:00:00',
    ...overrides,
  }
}

function makeBinding(
  overrides: Partial<RolePermission> & Pick<RolePermission, 'id' | 'roleId' | 'permissionId'>,
): RolePermission {
  return {
    allowedAction: 'VIEW',
    createdAt: '2026-11-01T10:00:00',
    ...overrides,
  }
}

const system: System = {
  id: 1,
  systemCode: 'ADMIN',
  systemName: '权限管理后台',
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-01T10:00:00',
  updatedAt: '2026-11-01T10:00:00',
}

describe('用户生效角色计算', () => {
  it('过滤过期/已回收授权，并沿父角色链继承', () => {
    const roles = [
      makeRole({ id: 12, roleName: '课程管理员', parentId: 10 }),
      makeRole({ id: 10, roleName: '公司领导' }),
      makeRole({ id: 30, roleName: '已过期角色' }),
      makeRole({ id: 40, roleName: '已回收角色' }),
    ]
    const grants = [
      makeGrant({ id: 1, roleId: 12 }),
      makeGrant({ id: 2, roleId: 30, expireTime: '2026-10-01T10:00:00+08:00' }),
      makeGrant({ id: 3, roleId: 40, status: 'REVOKED' }),
    ]

    const result = resolveEffectiveRoles(grants, roles, NOW)

    expect(result.map((role) => role.id)).toEqual([12, 10])
    expect(result[0]?.direct).toBe(true)
    expect(result[1]?.direct).toBe(false)
  })

  it('停用的父角色截断继承链', () => {
    const roles = [
      makeRole({ id: 21, roleName: '部门经理', parentId: 20 }),
      makeRole({ id: 20, roleName: '停用父角色', status: 'DISABLED', parentId: 19 }),
      makeRole({ id: 19, roleName: '祖父角色' }),
    ]

    const result = resolveEffectiveRoles(
      [makeGrant({ id: 1, roleId: 21 })],
      roles,
      NOW,
    )

    expect(result.map((role) => role.id)).toEqual([21])
  })
})

describe('用户详情角色与权限聚合', () => {
  beforeEach(() => {
    vi.mocked(listUserRoles).mockResolvedValue([
      makeGrant({ id: 1, roleId: 12 }),
      makeGrant({ id: 2, roleId: 21 }),
    ])
    vi.mocked(listRoles).mockResolvedValue([
      makeRole({ id: 12, roleName: '课程管理员', parentId: 10 }),
      makeRole({ id: 10, roleName: '公司领导' }),
      makeRole({ id: 21, roleName: '部门经理' }),
    ])
    vi.mocked(listSystems).mockResolvedValue([system])
    vi.mocked(listPermissions).mockResolvedValue([
      makePermission({ id: 20001, permissionName: '查询用户列表', action: 'VIEW' }),
      makePermission({ id: 20002, permissionName: '创建用户', action: 'ALL' }),
      makePermission({
        id: 20003,
        permissionName: '停用权限',
        action: 'VIEW',
        status: 'DISABLED',
      }),
    ])
    vi.mocked(getRolePermissions).mockImplementation(async (roleId) => {
      const bindings: Record<number, RolePermission[]> = {
        12: [
          makeBinding({ id: 1, roleId: 12, permissionId: 20001, allowedAction: 'VIEW' }),
          makeBinding({ id: 2, roleId: 12, permissionId: 20002, allowedAction: 'VIEW' }),
          makeBinding({ id: 3, roleId: 12, permissionId: 20003, allowedAction: 'ALL' }),
        ],
        10: [makeBinding({ id: 4, roleId: 10, permissionId: 20001, allowedAction: 'ALL' })],
        21: [makeBinding({ id: 5, roleId: 21, permissionId: 20002, allowedAction: 'ALL' })],
      }
      return bindings[roleId] ?? []
    })
  })

  it('合并继承角色权限、取最宽松动作并过滤无效节点', async () => {
    const authorization = useUserAuthorization()

    await authorization.load(16)

    expect(authorization.roles.value.map((role) => role.roleName)).toEqual([
      '课程管理员',
      '部门经理',
      '公司领导',
    ])
    expect(authorization.totalPermissions.value).toBe(2)

    const [admin] = authorization.groups.value
    expect(admin?.systemCode).toBe('ADMIN')
    const userList = admin?.permissions.find((item) => item.permissionId === 20001)
    expect(userList?.allowedAction).toBe('ALL')
    expect(userList?.sourceRoles).toEqual(['课程管理员', '公司领导'])

    const createUser = admin?.permissions.find((item) => item.permissionId === 20002)
    expect(createUser?.sourceRoles).toEqual(['部门经理'])
    expect(admin?.permissions.some((item) => item.permissionId === 20003)).toBe(false)
  })

  it('接口失败时返回可展示的错误状态', async () => {
    vi.mocked(listUserRoles).mockRejectedValue(new Error('无授权查询权限'))
    const authorization = useUserAuthorization()

    await authorization.load(16)

    expect(authorization.error.value).toBe('无授权查询权限')
    expect(authorization.roles.value).toEqual([])
    expect(authorization.loading.value).toBe(false)
  })
})
