import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import LoginPage from '@/views/login/LoginPage.vue'
import { ADMIN_ACTION_PERMISSIONS, ADMIN_MENU_PERMISSIONS } from '@/constants/permissions'
import { resetAccessState } from '@/router/access'
import { useAuthStore } from '@/stores/auth.store'
import { tokenStorage } from '@/utils/tokenStorage'

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  routeQuery: {} as Record<string, string>,
  login: vi.fn(),
  logout: vi.fn(),
  getUserPermissionIds: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: mocks.routeQuery }),
  useRouter: () => ({
    replace: mocks.replace,
    hasRoute: () => false,
    addRoute: vi.fn(),
    removeRoute: vi.fn(),
  }),
}))

vi.mock('@/api', () => ({
  authApi: {
    login: mocks.login,
    logout: mocks.logout,
  },
  openApi: {
    getUserPermissionIds: mocks.getUserPermissionIds,
  },
}))

async function submitCredentials(wrapper: VueWrapper, username: string, password: string) {
  await wrapper.get('input[autocomplete="username"]').setValue(username)
  await wrapper.get('input[autocomplete="current-password"]').setValue(password)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('登录页后台权限拦截与错误提示', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    resetAccessState()
    mocks.routeQuery = {}
    mocks.replace.mockReset()
    mocks.replace.mockResolvedValue(undefined)
    mocks.login.mockReset()
    mocks.logout.mockReset()
    mocks.logout.mockResolvedValue(undefined)
    mocks.getUserPermissionIds.mockReset()

    mocks.login.mockResolvedValue({
      accessToken: 'issued-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      userId: '192',
      displayName: '测试账号',
      accountStatus: 'NORMAL',
    })
  })

  it('无 ADMIN 菜单权限账号登录时自动登出吊销令牌并留在登录页提示', async () => {
    mocks.getUserPermissionIds.mockResolvedValueOnce([])
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: () => {} }))

    const wrapper = mount(LoginPage, {
      global: {
        plugins: [ElementPlus],
      },
    })

    await submitCredentials(wrapper, 'SG000191', 'Pass@123')

    expect(mocks.getUserPermissionIds).toHaveBeenCalledWith(192, false)
    expect(mocks.logout).toHaveBeenCalledTimes(1)
    expect(useAuthStore().hasValidSession).toBe(false)
    expect(tokenStorage.getAccessToken()).toBeNull()
    expect(mocks.replace).not.toHaveBeenCalled()
    expect(errorSpy).toHaveBeenCalledWith('当前账号暂无权限管理后台访问权限，请使用管理员账号登录')
  })

  it('仅有按钮权限而无菜单权限时同样拦截登录并吊销令牌', async () => {
    mocks.getUserPermissionIds.mockResolvedValueOnce([ADMIN_ACTION_PERMISSIONS.USER_LIST])
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: () => {} }))

    const wrapper = mount(LoginPage, {
      global: {
        plugins: [ElementPlus],
      },
    })

    await submitCredentials(wrapper, 'SG000001', 'Pass@123')

    expect(mocks.logout).toHaveBeenCalledTimes(1)
    expect(useAuthStore().hasValidSession).toBe(false)
    expect(mocks.replace).not.toHaveBeenCalled()
    expect(errorSpy).toHaveBeenCalledWith('当前账号暂无权限管理后台访问权限，请使用管理员账号登录')
  })

  it('登录接口抛出 BusinessError 时不重复弹出兜底“登录失败”', async () => {
    mocks.login.mockRejectedValueOnce({ code: 2001, message: '账号或密码错误。' })
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: () => {} }))

    const wrapper = mount(LoginPage, {
      global: {
        plugins: [ElementPlus],
      },
    })

    await submitCredentials(wrapper, 'admin', 'wrong')

    expect(mocks.replace).not.toHaveBeenCalled()
    expect(errorSpy).not.toHaveBeenCalled()
  })

  it('拥有 ADMIN 菜单权限时正常跳转，且忽略 redirect=/403 回退至首页', async () => {
    mocks.routeQuery = { redirect: '/403' }
    mocks.getUserPermissionIds.mockResolvedValueOnce([ADMIN_MENU_PERMISSIONS.HOME])

    const wrapper = mount(LoginPage, {
      global: {
        plugins: [ElementPlus],
      },
    })

    await submitCredentials(wrapper, 'admin', 'Admin@123')

    expect(mocks.logout).not.toHaveBeenCalled()
    expect(mocks.replace).toHaveBeenCalledWith('/')
  })
})
