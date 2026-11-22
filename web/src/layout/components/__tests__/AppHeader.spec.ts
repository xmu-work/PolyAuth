import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import AppHeader from '@/layout/components/AppHeader.vue'
import ChangePasswordDialog from '@/layout/components/ChangePasswordDialog.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn() }),
}))

describe('FR-34 修改密码入口', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('FR-34 顶栏提供个人修改密码入口', async () => {
    const wrapper = mount(AppHeader, {
      global: {
        plugins: [ElementPlus],
      },
    })

    await wrapper.get('[data-testid="open-change-password"]').trigger('click')
    await flushPromises()

    expect(wrapper.findComponent(ChangePasswordDialog).props('modelValue')).toBe(true)
  })
})
