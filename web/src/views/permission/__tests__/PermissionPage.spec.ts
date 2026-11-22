import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import PermissionPage from '@/views/permission/PermissionPage.vue'
import { listRoles } from '@/api/role.api'
import {
  createApi,
  createPermission,
  getPermissionApis,
  getRolePermissions,
  listApis,
  listPermissions,
  listSystems,
  unbindApiFromPermission,
  unbindRolePermissions,
  bindApiToPermission,
  bindRolePermissions,
} from '@/api/system.api'
import type { Role } from '@/types/role.types'
import type { Permission, System } from '@/types/system.types'

vi.mock('@/api/role.api', () => ({
  listRoles: vi.fn(),
}))

vi.mock('@/api/system.api', () => ({
  bindApiToPermission: vi.fn(),
  bindRolePermissions: vi.fn(),
  createApi: vi.fn(),
  createPermission: vi.fn(),
  getPermissionApis: vi.fn(),
  getRolePermissions: vi.fn(),
  listApis: vi.fn(),
  listPermissions: vi.fn(),
  listSystems: vi.fn(),
  unbindApiFromPermission: vi.fn(),
  unbindRolePermissions: vi.fn(),
}))

const system: System = {
  id: 1,
  systemCode: 'ADMIN',
  systemName: '管理后台',
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const permission: Permission = {
  id: 10003,
  systemId: 1,
  permissionCode: 'MENU_USER',
  permissionName: '用户管理',
  permissionType: 'MENU',
  action: 'VIEW',
  routePath: '/user',
  sortNo: 3,
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const role: Role = {
  id: 12,
  roleCode: 'ADMIN',
  roleName: '系统管理员',
  roleType: 'FUNC',
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

describe('FR-17 功能权限页面', () => {
  beforeEach(() => {
    vi.mocked(listSystems).mockResolvedValue([system])
    vi.mocked(listPermissions).mockResolvedValue([permission])
    vi.mocked(listRoles).mockResolvedValue([role])
    vi.mocked(listApis).mockResolvedValue([])
    vi.mocked(getPermissionApis).mockResolvedValue([])
    vi.mocked(getRolePermissions).mockResolvedValue([])
    vi.mocked(createApi).mockResolvedValue({
      id: 1,
      systemId: 1,
      apiCode: 'TEST',
      apiName: '测试',
      apiPath: '/test',
      httpMethod: 'GET',
      status: 'ACTIVE',
      isDeleted: 0,
      createdAt: '2026-11-22T10:00:00',
      updatedAt: '2026-11-22T10:00:00',
    })
    vi.mocked(createPermission).mockResolvedValue(permission)
    vi.mocked(bindApiToPermission).mockResolvedValue({
      id: 1,
      systemId: 1,
      permissionId: 10003,
      apiCode: 'TEST',
      apiName: '测试',
      apiPath: '/test',
      httpMethod: 'GET',
      status: 'ACTIVE',
      isDeleted: 0,
      createdAt: '2026-11-22T10:00:00',
      updatedAt: '2026-11-22T10:00:00',
    })
    vi.mocked(bindRolePermissions).mockResolvedValue()
    vi.mocked(unbindApiFromPermission).mockResolvedValue()
    vi.mocked(unbindRolePermissions).mockResolvedValue()
  })

  it('FR-17 默认选择首个系统并展示该系统权限树', async () => {
    const wrapper = mount(PermissionPage, {
      global: {
        plugins: [createPinia(), ElementPlus],
        directives: { perm: {} },
      },
    })
    await flushPromises()

    expect(listPermissions).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('管理后台')
    expect(wrapper.text()).toContain('用户管理')
  })
})
