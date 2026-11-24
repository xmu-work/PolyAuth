import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import ForbiddenPage from '@/views/error/ForbiddenPage.vue'
import { ADMIN_ACTION_PERMISSIONS, ADMIN_MENU_PERMISSIONS } from '@/constants/permissions'
import { resetAccessState } from '@/router/access'
import { useAuthStore } from '@/stores/auth.store'
import { usePermissionStore } from '@/stores/permission.store'
import { tokenStorage } from '@/utils/tokenStorage'

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  logout: vi.fn(),
  getUserPermissionIds: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: mocks.replace }),
}))

vi.mock('@/api', () => ({
  authApi: {
    logout: mocks.logout,
  },
  openApi: {
    getUserPermissionIds: mocks.getUserPermissionIds,
  },
}))

describe('403 无访问权限页交互与退出口', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    resetAccessState()
    mocks.replace.mockReset()
    mocks.replace.mockResolvedValue(undefined)
    mocks.logout.mockReset()
    mocks.logout.mockResolvedValue(undefined)
    mocks.getUserPermissionIds.mockReset()
    mocks.getUserPermissionIds.mockResolvedValue([])

    tokenStorage.save({
      accessToken: 'student-token',
      userId: 192,
      displayName: '测试学生',
      expiresIn: 7200,
    })
    useAuthStore().restore()
  })

  it('无可访问菜单权限时隐藏返回首页按钮，点击退出登录/切换账号后吊销令牌并跳登录页', async () => {
    usePermissionStore().setPermissions([])

    const wrapper = mount(ForbiddenPage, {
      global: {
        plugins: [ElementPlus],
      },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('返回首页')

    const switchButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('退出登录 / 切换账号'))
    expect(switchButton).toBeDefined()

    await switchButton?.trigger('click')
    await flushPromises()

    expect(mocks.logout).toHaveBeenCalledTimes(1)
    expect(useAuthStore().hasValidSession).toBe(false)
    expect(tokenStorage.getAccessToken()).toBeNull()
    expect(mocks.replace).toHaveBeenCalledWith('/login')
  })

  it('仅有按钮权限时点击重新拉取权限仍停留在 /403 不跳转首页', async () => {
    usePermissionStore().setPermissions([ADMIN_ACTION_PERMISSIONS.USER_LIST])
    mocks.getUserPermissionIds.mockResolvedValueOnce([ADMIN_ACTION_PERMISSIONS.USER_LIST])
    const warningSpy = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({ close: () => {} }))

    const wrapper = mount(ForbiddenPage, {
      global: {
        plugins: [ElementPlus],
      },
    })

    const retryButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('重新拉取权限'))
    expect(retryButton).toBeDefined()

    await retryButton?.trigger('click')
    await flushPromises()

    expect(mocks.getUserPermissionIds).toHaveBeenCalledWith(192, false)
    expect(mocks.replace).not.toHaveBeenCalled()
    expect(warningSpy).toHaveBeenCalledWith('当前账号仍无可访问的管理后台菜单')
  })

  it('拥有至少一个菜单权限时展示返回首页按钮并可跳回首页', async () => {
    usePermissionStore().setPermissions([ADMIN_MENU_PERMISSIONS.HOME])

    const wrapper = mount(ForbiddenPage, {
      global: {
        plugins: [ElementPlus],
      },
    })
    await flushPromises()

    const homeButton = wrapper.findAll('button').find((button) => button.text().includes('返回首页'))
    expect(homeButton).toBeDefined()

    await homeButton?.trigger('click')
    await flushPromises()

    expect(mocks.replace).toHaveBeenCalledWith('/')
  })
})
