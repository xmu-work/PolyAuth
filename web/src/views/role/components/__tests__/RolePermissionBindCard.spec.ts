import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import RolePermissionBindCard from '@/views/role/components/RolePermissionBindCard.vue'
import { listRoles } from '@/api/role.api'
import {
  getRolePermissions,
  listPermissions,
  listSystems,
} from '@/api/system.api'
import type { Role } from '@/types/role.types'
import type { Permission, RolePermission, System } from '@/types/system.types'

vi.mock('@/api/role.api', () => ({
  listRoles: vi.fn(),
}))

vi.mock('@/api/system.api', () => ({
  getRolePermissions: vi.fn(),
  listPermissions: vi.fn(),
  listSystems: vi.fn(),
}))

const role: Role = {
  id: 12,
  roleName: '测试角色',
  roleType: 'FUNC',
  parentIds: [5],
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const parentRole: Role = {
  id: 5,
  roleName: '普通员工',
  roleType: 'FUNC',
  parentIds: [],
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const system: System = {
  id: 1,
  systemCode: 'ADMIN',
  systemName: '权限管理后台',
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

function permission(
  id: number,
  parentId: number | undefined,
  name: string,
  code = `CODE_${id}`,
): Permission {
  return {
    id,
    systemId: 1,
    parentId,
    permissionCode: code,
    permissionName: name,
    permissionType: parentId === undefined ? 'MENU' : 'BUTTON',
    action: 'VIEW',
    sortNo: id,
    status: 'ACTIVE',
    isDeleted: 0,
    createdAt: '2026-11-22T10:00:00',
    updatedAt: '2026-11-22T10:00:00',
  }
}

function binding(permissionId: number): RolePermission {
  return {
    id: permissionId,
    roleId: 12,
    permissionId,
    allowedAction: 'VIEW',
    createdAt: '2026-11-22T10:00:00',
  }
}

describe('FR-27 角色权限只读查看', () => {
  beforeEach(() => {
    vi.mocked(listSystems).mockResolvedValue([system])
    vi.mocked(listRoles).mockResolvedValue([role, parentRole])
    vi.mocked(listPermissions).mockResolvedValue([
      permission(10003, undefined, '用户管理'),
      permission(20001, 10003, '查询用户'),
      permission(20002, 10003, '新建用户'),
      permission(10009, undefined, '数据查看', 'DATA_VIEW'),
    ])
    vi.mocked(getRolePermissions).mockImplementation(async (roleId: number) =>
      roleId === 5 ? [binding(20002), binding(10009)] : [binding(20001), binding(10009)],
    )
  })

  it('展示直接与继承权限并标注来源角色', async () => {
    const wrapper = mount(RolePermissionBindCard, {
      props: { roleId: 12 },
      global: {
        plugins: [ElementPlus],
        directives: { perm: {} },
      },
    })
    await flushPromises()

    expect(getRolePermissions).toHaveBeenCalledWith(12)
    expect(getRolePermissions).toHaveBeenCalledWith(5)
    expect(wrapper.text()).toContain('查询用户')
    expect(wrapper.text()).toContain('新建用户')
    expect(wrapper.text()).toContain('普通员工')
    expect(wrapper.text()).toContain('继承')
    expect(wrapper.find('.el-table').text()).not.toContain('数据查看')
    expect(wrapper.text()).not.toContain('操作码')
    expect(wrapper.text()).not.toContain('当前授权')
  })
})
