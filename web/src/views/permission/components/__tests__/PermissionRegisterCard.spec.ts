import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import PermissionRegisterCard from '@/views/permission/components/PermissionRegisterCard.vue'
import { createPermission } from '@/api/system.api'
import type { Permission, System } from '@/types/system.types'

vi.mock('@/api/system.api', () => ({
  createPermission: vi.fn(),
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

const parentPermission: Permission = {
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

describe('FR-17 前端权限登记', () => {
  beforeEach(() => {
    vi.mocked(createPermission).mockResolvedValue(parentPermission)
  })

  it('FR-17 通过权限树选择父节点并提交完整操作码', async () => {
    const wrapper = mount(PermissionRegisterCard, {
      props: {
        systems: [system],
        permissions: [parentPermission],
      },
      global: {
        plugins: [ElementPlus],
        directives: { perm: {} },
      },
    })

    expect(wrapper.text()).not.toContain('父权限 ID（选填）')

    ;(wrapper.findComponent('[data-testid="system-select"]') as VueWrapper).vm.$emit(
      'update:modelValue',
      1,
    )
    ;(wrapper.findComponent('[data-testid="action-select"]') as VueWrapper).vm.$emit(
      'update:modelValue',
      'VIEW_EDIT',
    )

    const parentSelect = wrapper.findComponent({ name: 'ElTreeSelect' })
    parentSelect.vm.$emit('update:modelValue', 10003)

    await wrapper.get('[data-testid="permission-code"]').setValue('BTN_USER_EDIT')
    await wrapper.get('[data-testid="permission-name"]').setValue('编辑用户')
    await wrapper.get('[data-testid="route-path"]').setValue('/user')
    await wrapper.get('[data-testid="create-permission"]').trigger('click')
    await flushPromises()

    expect(createPermission).toHaveBeenCalledWith({
      systemId: 1,
      parentId: 10003,
      permissionCode: 'BTN_USER_EDIT',
      permissionName: '编辑用户',
      permissionType: 'MENU',
      routePath: '/user',
      action: 'VIEW_EDIT',
      sortNo: undefined,
    })
  })
})
