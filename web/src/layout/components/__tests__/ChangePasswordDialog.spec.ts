import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import ChangePasswordDialog from '@/layout/components/ChangePasswordDialog.vue'
import { changePassword } from '@/api/auth.api'

vi.mock('@/api/auth.api', () => ({
  changePassword: vi.fn(),
}))

describe('FR-34 自助修改密码', () => {
  beforeEach(() => {
    vi.mocked(changePassword).mockResolvedValue()
  })

  it('FR-34 原密码与新密码匹配后可完成修改', async () => {
    const wrapper = mount(ChangePasswordDialog, {
      props: { modelValue: true },
      global: {
        plugins: [ElementPlus],
        stubs: { teleport: true },
      },
    })
    await flushPromises()

    await wrapper.get('[data-testid="old-password"]').setValue('Admin123')
    await wrapper.get('[data-testid="new-password"]').setValue('Changed456')
    await wrapper.get('[data-testid="confirm-password"]').setValue('Changed456')
    await wrapper.get('[data-testid="submit-password"]').trigger('click')
    await flushPromises()

    expect(changePassword).toHaveBeenCalledWith({
      oldPassword: 'Admin123',
      newPassword: 'Changed456',
    })
    expect(wrapper.emitted('success')).toHaveLength(1)
  })

  it('FR-34 两次新密码不一致时不提交修改', async () => {
    const wrapper = mount(ChangePasswordDialog, {
      props: { modelValue: true },
      global: {
        plugins: [ElementPlus],
        stubs: { teleport: true },
      },
    })
    await flushPromises()

    await wrapper.get('[data-testid="old-password"]').setValue('Admin123')
    await wrapper.get('[data-testid="new-password"]').setValue('Changed456')
    await wrapper.get('[data-testid="confirm-password"]').setValue('Changed789')
    await wrapper.get('[data-testid="submit-password"]').trigger('click')
    await flushPromises()

    expect(changePassword).not.toHaveBeenCalled()
  })
})
