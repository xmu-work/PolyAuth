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

describe('FR-38 菜单切换权限校验日志', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    testContext.push.mockReset()
    testContext.checkPermission.mockReset()
    testContext.checkPermission.mockResolvedValue(true)
    usePermissionStore().setPermissions(Object.values(ADMIN_MENU_PERMISSIONS))
  })

  it('FR-38 点击菜单后先请求后端权限校验再跳转', async () => {
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

    expect(testContext.checkPermission).toHaveBeenCalledWith('MENU_ORGANIZATION')
    expect(testContext.push).toHaveBeenCalledWith('/organization')
  })

  it('FR-38 后端拒绝菜单权限时跳转无权限页', async () => {
    testContext.checkPermission.mockResolvedValue(false)
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

    expect(testContext.checkPermission).toHaveBeenCalledWith('MENU_ORGANIZATION')
    expect(testContext.push).toHaveBeenCalledWith('/403')
    expect(testContext.push).not.toHaveBeenCalledWith('/organization')
  })
})
