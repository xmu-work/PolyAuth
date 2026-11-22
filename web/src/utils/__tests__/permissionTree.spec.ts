import { describe, expect, it } from 'vitest'
import { buildPermissionTree } from '@/utils/permissionTree'
import type { Permission } from '@/types/system.types'

function permission(
  id: number,
  parentId: number | undefined,
  permissionName: string,
  sortNo: number,
): Permission {
  return {
    id,
    systemId: 1,
    parentId,
    permissionCode: `PERMISSION_${id}`,
    permissionName,
    permissionType: parentId === undefined ? 'MENU' : 'BUTTON',
    action: 'VIEW',
    sortNo,
    status: 'ACTIVE',
    isDeleted: 0,
    createdAt: '2026-11-22T10:00:00',
    updatedAt: '2026-11-22T10:00:00',
  }
}

describe('FR-17 前端权限树', () => {
  it('FR-17 权限列表按父子关系组装为有序权限树', () => {
    const tree = buildPermissionTree([
      permission(30002, 10002, '查看用户', 2),
      permission(10001, undefined, '系统管理', 1),
      permission(20001, 10001, '角色管理', 1),
      permission(10002, undefined, '用户管理', 2),
      permission(30001, 10002, '新建用户', 2),
    ])

    expect(
      tree.map((node) => ({
        name: node.permissionName,
        children: node.children.map((child) => child.permissionName),
      })),
    ).toEqual([
      { name: '系统管理', children: ['角色管理'] },
      { name: '用户管理', children: ['新建用户', '查看用户'] },
    ])
  })
})
