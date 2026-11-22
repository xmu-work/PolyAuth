import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import RolePermissionBindCard from '@/views/permission/components/RolePermissionBindCard.vue'
import { listRoles } from '@/api/role.api'
import {
  bindRolePermissions,
  getRolePermissions,
  unbindRolePermissions,
} from '@/api/system.api'
import type { Role } from '@/types/role.types'
import type { Permission } from '@/types/system.types'

vi.mock('@/api/role.api', () => ({
  listRoles: vi.fn(),
}))

vi.mock('@/api/system.api', () => ({
  bindRolePermissions: vi.fn(),
  getRolePermissions: vi.fn(),
  unbindRolePermissions: vi.fn(),
}))

const role: Role = {
  id: 12,
  roleCode: 'FINANCE',
  roleName: '财务专员',
  roleType: 'FUNC',
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

function permission(id: number, parentId: number | undefined, name: string): Permission {
  return {
    id,
    systemId: 1,
    parentId,
    permissionCode: `CODE_${id}`,
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

describe('FR-27 角色功能权限授予', () => {
  beforeEach(() => {
    vi.mocked(listRoles).mockResolvedValue([role])
    vi.mocked(getRolePermissions).mockResolvedValue([])
    vi.mocked(bindRolePermissions).mockResolvedValue()
    vi.mocked(unbindRolePermissions).mockResolvedValue()
  })

  it('FR-27 通过角色和权限树选择并以所选操作码批量授权', async () => {
    const wrapper = mount(RolePermissionBindCard, {
      props: {
        permissions: [
          permission(10003, undefined, '用户管理'),
          permission(20001, 10003, '查询用户'),
          permission(20002, 10003, '新建用户'),
        ],
      },
      global: {
        plugins: [ElementPlus],
        directives: { perm: {} },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('查询用户')

    const selects = wrapper.findAllComponents({ name: 'ElSelect' })
    selects[0]?.vm.$emit('update:modelValue', role.id)
    selects[1]?.vm.$emit('update:modelValue', 'VIEW_EDIT')

    const tree = wrapper.findComponent({ name: 'ElTree' })
    tree.vm.$emit('check', {}, { checkedKeys: [20001, 20002] })
    await wrapper.get('[data-testid="bind-permissions"]').trigger('click')
    await flushPromises()

    expect(bindRolePermissions).toHaveBeenCalledWith({
      roleId: 12,
      permissionIds: [20001, 20002],
      allowedAction: 'VIEW_EDIT',
    })
  })
})
