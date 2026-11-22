import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import ApiRegisterCard from '@/views/permission/components/ApiRegisterCard.vue'
import { bindApiToPermission, createApi } from '@/api/system.api'
import type { Api, System } from '@/types/system.types'

vi.mock('@/api/system.api', () => ({
  bindApiToPermission: vi.fn(),
  createApi: vi.fn(),
}))

const system: System = {
  id: 1,
  systemCode: 'ADMIN',
  systemName: '管理后台',
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const api: Api = {
  id: 31001,
  systemId: 1,
  apiCode: 'DOC_EXPORT',
  apiName: '导出文档',
  apiPath: '/documents/{documentId}/export',
  httpMethod: 'POST',
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

describe('FR-19 API 独立登记', () => {
  beforeEach(() => {
    vi.mocked(createApi).mockResolvedValue(api)
    vi.mocked(bindApiToPermission).mockResolvedValue(api)
  })

  it('FR-19 登记 API 时只创建目录记录，不偷偷建立权限映射', async () => {
    const wrapper = mount(ApiRegisterCard, {
      props: { systems: [system] },
      global: {
        plugins: [ElementPlus],
        directives: { perm: {} },
      },
    })

    expect(wrapper.text()).not.toContain('所属权限 ID')

    ;(wrapper.findComponent('[data-testid="api-system-select"]') as VueWrapper).vm.$emit(
      'update:modelValue',
      1,
    )
    await wrapper.get('[data-testid="api-code"]').setValue('DOC_EXPORT')
    await wrapper.get('[data-testid="api-name"]').setValue('导出文档')
    await wrapper.get('[data-testid="api-path"]').setValue('/documents/{documentId}/export')
    await wrapper.get('[data-testid="create-api"]').trigger('click')
    await flushPromises()

    expect(createApi).toHaveBeenCalledWith({
      systemId: 1,
      apiCode: 'DOC_EXPORT',
      apiName: '导出文档',
      apiPath: '/documents/{documentId}/export',
      httpMethod: 'GET',
    })
    expect(bindApiToPermission).not.toHaveBeenCalled()
  })
})
