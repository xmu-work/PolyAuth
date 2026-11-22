import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import PermissionTreeCard from '@/views/permission/components/PermissionTreeCard.vue'
import type { Permission } from '@/types/system.types'

function permission(
  id: number,
  parentId: number | undefined,
  permissionName: string,
  permissionType: 'MENU' | 'BUTTON',
): Permission {
  return {
    id,
    systemId: 1,
    parentId,
    permissionCode: `CODE_${id}`,
    permissionName,
    permissionType,
    action: 'VIEW',
    sortNo: id,
    status: 'ACTIVE',
    isDeleted: 0,
    createdAt: '2026-11-22T10:00:00',
    updatedAt: '2026-11-22T10:00:00',
  }
}

describe('FR-17 前端权限树管理', () => {
  it('FR-17 界面按层级展示权限并可选择节点', async () => {
    const permissions = [
      permission(10003, undefined, '用户管理', 'MENU'),
      permission(20001, 10003, '查询用户', 'BUTTON'),
      permission(20002, 10003, '新建用户', 'BUTTON'),
    ]
    const wrapper = mount(PermissionTreeCard, {
      props: { permissions },
      global: { plugins: [ElementPlus] },
    })

    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('查询用户')
    expect(wrapper.text()).toContain('新建用户')

    await wrapper.get('[data-permission-id="20002"]').trigger('click')

    expect(wrapper.emitted('select')?.[0]).toEqual([20002])
  })
})
