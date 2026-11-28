import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ADMIN_ACTION_PERMISSIONS, ADMIN_MENU_PERMISSIONS } from '@/constants/permissions'
import { useAppStore } from '@/stores/app.store'
import { useAuthStore } from '@/stores/auth.store'
import { usePermissionStore } from '@/stores/permission.store'
import { tokenStorage } from '@/utils/tokenStorage'
import { resetAccessState } from '@/router/access'
import router, { installRouterGuards } from '@/router'

const apiMocks = vi.hoisted(() => ({
  validateSession: vi.fn(),
  checkPermission: vi.fn(),
  getUserPermissionIds: vi.fn(),
}))

vi.mock('@/api', () => ({
  authApi: {
    validateSession: apiMocks.validateSession,
    checkPermission: apiMocks.checkPermission,
  },
  openApi: {
    getUserPermissionIds: apiMocks.getUserPermissionIds,
  },
}))

describe('FR-38 路由守卫菜单权限校验全覆盖', () => {
  beforeEach(async () => {
    localStorage.clear()
    setActivePinia(createPinia())
    resetAccessState()
    installRouterGuards()

    apiMocks.validateSession.mockReset()
    apiMocks.checkPermission.mockReset()
    apiMocks.getUserPermissionIds.mockReset()

    apiMocks.validateSession.mockResolvedValue(true)
    apiMocks.checkPermission.mockResolvedValue(true)
    apiMocks.getUserPermissionIds.mockResolvedValue(Object.values(ADMIN_MENU_PERMISSIONS))

    tokenStorage.save({
      accessToken: 'valid-token',
      userId: 1,
      displayName: '管理员',
      expiresIn: 7200,
    })
    useAuthStore().restore()
    await router.replace('/login')
    apiMocks.checkPermission.mockClear()
  })

  it('登录后首屏进入首页只调用一次 MENU_HOME 校验', async () => {
    await router.push('/')

    expect(router.currentRoute.value.path).toBe('/home')
    expect(apiMocks.checkPermission).toHaveBeenCalledTimes(1)
    expect(apiMocks.checkPermission).toHaveBeenCalledWith('MENU_HOME')
    expect(useAppStore().tabs.map((tab) => tab.path)).toEqual(['/home'])
  })

  it('经路由从首页切到组织管理并切回首页（模拟顶栏 Tab 或历史回退）均触发对应校验', async () => {
    await router.push('/')
    apiMocks.checkPermission.mockClear()

    await router.push('/organization')
    expect(router.currentRoute.value.path).toBe('/organization')
    expect(apiMocks.checkPermission).toHaveBeenCalledTimes(1)
    expect(apiMocks.checkPermission).toHaveBeenLastCalledWith('MENU_ORGANIZATION')

    await router.push('/home')
    expect(router.currentRoute.value.path).toBe('/home')
    expect(apiMocks.checkPermission).toHaveBeenCalledTimes(2)
    expect(apiMocks.checkPermission).toHaveBeenLastCalledWith('MENU_HOME')
  })

  it('后端返回 allowed=false 时跳转 /403 且不写入目标页 Tab', async () => {
    await router.push('/')
    apiMocks.checkPermission.mockClear()
    apiMocks.checkPermission.mockResolvedValueOnce(false)

    await router.push('/role')

    expect(apiMocks.checkPermission).toHaveBeenCalledTimes(1)
    expect(apiMocks.checkPermission).toHaveBeenCalledWith('MENU_ROLE')
    expect(router.currentRoute.value.path).toBe('/403')
    expect(useAppStore().tabs.map((tab) => tab.path)).toEqual(['/home'])
  })

  it('后端校验请求异常时取消本次导航，停留在原菜单', async () => {
    await router.push('/')
    apiMocks.checkPermission.mockClear()
    apiMocks.checkPermission.mockRejectedValueOnce(new Error('network error'))

    await router.push('/user')

    expect(apiMocks.checkPermission).toHaveBeenCalledTimes(1)
    expect(apiMocks.checkPermission).toHaveBeenCalledWith('MENU_USER')
    expect(router.currentRoute.value.path).toBe('/home')
    expect(usePermissionStore().count).toBeGreaterThan(0)
  })

  it('无任何后台菜单权限时访问首页跳 /403，且再次导航到 /login 不会死循环踢回 /403', async () => {
    apiMocks.getUserPermissionIds.mockResolvedValueOnce([])

    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/403')

    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('仅有按钮权限而无菜单权限时访问首页跳 /403，且可正常进入 /login 切换账号', async () => {
    apiMocks.getUserPermissionIds.mockResolvedValueOnce([ADMIN_ACTION_PERMISSIONS.USER_LIST])

    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/403')

    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
