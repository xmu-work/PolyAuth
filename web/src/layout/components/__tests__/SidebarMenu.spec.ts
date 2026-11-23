import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import SidebarMenu from '@/layout/components/SidebarMenu.vue'
import { ADMIN_MENU_PERMISSIONS } from '@/constants/permissions'
import { usePermissionStore } from '@/stores/permission.store'

const testContext = vi.hoisted(() => ({
  push: vi.fn(),
  currentPath: '/home',
  checkPermission: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: testContext.currentPath }),
  useRouter: () => ({ push: testContext.push }),
}))

vi.mock('@/api', () => ({
  authApi: {
    checkPermission: testContext.checkPermission,
  },
}))

describe('FR-38 侧栏菜单导航委托路由守卫', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    testContext.push.mockReset()
    testContext.push.mockResolvedValue(undefined)
    testContext.checkPermission.mockReset()
    testContext.currentPath = '/home'
    usePermissionStore().setPermissions(Object.values(ADMIN_MENU_PERMISSIONS))
  })

  it('点击其他菜单时委托 router.push 跳转且不在组件内重复直调 checkPermission', async () => {
    const wrapper = mount(SidebarMenu, {
      global: {
        plugins: [ElementPlus],
      },
    })

    const organizationItem = wrapper
      .findAll('.el-menu-item')
      .find((item) => item.text().includes('组织管理'))

    expect(organizationItem).toBeDefined()
    await organizationItem?.trigger('click')
    await flushPromises()

    expect(testContext.push).toHaveBeenCalledTimes(1)
    expect(testContext.push).toHaveBeenCalledWith('/organization')
    expect(testContext.checkPermission).not.toHaveBeenCalled()
  })

  it('点击当前所在菜单时不重复触发路由跳转', async () => {
    const wrapper = mount(SidebarMenu, {
      global: {
        plugins: [ElementPlus],
      },
    })

    const homeItem = wrapper
      .findAll('.el-menu-item')
      .find((item) => item.text().includes('首页'))

    expect(homeItem).toBeDefined()
    await homeItem?.trigger('click')
    await flushPromises()

    expect(testContext.push).not.toHaveBeenCalled()
    expect(testContext.checkPermission).not.toHaveBeenCalled()
  })
})
