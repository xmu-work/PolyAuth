import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import RoleMembersCard from '@/views/role/components/RoleMembersCard.vue'
import { listRoleAssignments } from '@/api/role.api'
import { getUser } from '@/api/user.api'
import type { UserRole } from '@/types/role.types'
import type { User } from '@/types/user.types'

vi.mock('@/api/role.api', () => ({
  listRoleAssignments: vi.fn(),
}))

vi.mock('@/api/user.api', () => ({
  getUser: vi.fn(),
}))

const assignment: UserRole = {
  id: 9001,
  userId: 16,
  roleId: 12,
  effectiveTime: '2026-11-22T10:00:00',
  status: 'ACTIVE',
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const user: User = {
  id: 16,
  userNo: 'zhangsan',
  realName: '张三',
  orgId: 3,
  accountStatus: 'NORMAL',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

describe('FR-07 角色成员查询', () => {
  beforeEach(() => {
    vi.mocked(listRoleAssignments).mockResolvedValue([assignment])
    vi.mocked(getUser).mockResolvedValue(user)
  })

  it('FR-07 角色详情展示已授予该角色的用户', async () => {
    const wrapper = mount(RoleMembersCard, {
      props: { roleId: 12 },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(listRoleAssignments).toHaveBeenCalledWith(12)
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('zhangsan')
  })

  it('FR-07 用户详情不可读时仍展示角色授权记录', async () => {
    vi.mocked(getUser).mockRejectedValue(new Error('无用户详情权限'))
    const wrapper = mount(RoleMembersCard, {
      props: { roleId: 12 },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('用户 #16')
    expect(wrapper.text()).not.toContain('角色成员加载失败')
  })
})
