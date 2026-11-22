import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePermissionCatalog } from '@/composables/usePermissionCatalog'
import { listPermissions } from '@/api/system.api'
import type { Permission } from '@/types/system.types'

vi.mock('@/api/system.api', () => ({
  listPermissions: vi.fn(),
}))

const permission: Permission = {
  id: 10001,
  systemId: 1,
  permissionCode: 'MENU_HOME',
  permissionName: '首页',
  permissionType: 'MENU',
  action: 'VIEW',
  routePath: '/home',
  sortNo: 1,
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

describe('FR-17 前端权限树管理', () => {
  beforeEach(() => {
    vi.mocked(listPermissions).mockResolvedValue([permission])
  })

  it('FR-17 切换系统后加载该系统的权限树', async () => {
    const catalog = usePermissionCatalog()

    await catalog.selectSystem(1)

    expect(listPermissions).toHaveBeenCalledWith(1)
    expect(catalog.selectedSystemId.value).toBe(1)
    expect(catalog.permissions.value.map((item) => item.id)).toEqual([10001])
  })
})
