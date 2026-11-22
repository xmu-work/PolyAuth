import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus, { ElMessageBox, type MessageBoxData } from 'element-plus'
import ApiMappingCard from '@/views/permission/components/ApiMappingCard.vue'
import {
  bindApiToPermission,
  getPermissionApis,
  listApis,
  unbindApiFromPermission,
} from '@/api/system.api'
import type { Api, Permission } from '@/types/system.types'

vi.mock('@/api/system.api', () => ({
  bindApiToPermission: vi.fn(),
  getPermissionApis: vi.fn(),
  listApis: vi.fn(),
  unbindApiFromPermission: vi.fn(),
}))

const permission: Permission = {
  id: 20001,
  systemId: 1,
  parentId: 10003,
  permissionCode: 'USER_LIST',
  permissionName: '查询用户列表',
  permissionType: 'BUTTON',
  action: 'VIEW',
  sortNo: 1,
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const mappedApi: Api = {
  id: 30001,
  systemId: 1,
  permissionId: 20001,
  apiCode: 'USER_LIST',
  apiName: '查询用户',
  apiPath: '/users',
  httpMethod: 'GET',
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const unmappedApi: Api = {
  ...mappedApi,
  id: 30002,
  permissionId: null,
  apiCode: 'USER_GET',
  apiName: '查询用户详情',
  apiPath: '/users/{userId}',
}

describe('FR-19 API 与前端权限映射', () => {
  beforeEach(() => {
    vi.mocked(listApis).mockResolvedValue([mappedApi, unmappedApi])
    vi.mocked(getPermissionApis).mockResolvedValue([mappedApi])
    vi.mocked(bindApiToPermission).mockResolvedValue({ ...unmappedApi, permissionId: 20001 })
    vi.mocked(unbindApiFromPermission).mockResolvedValue()
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(
      { value: '', action: 'confirm' } as unknown as MessageBoxData,
    )
  })

  it('FR-19 可查看并解除前端权限当前映射的 API', async () => {
    const wrapper = mount(ApiMappingCard, {
      props: {
        systemId: 1,
        permission,
      },
      global: {
        plugins: [ElementPlus],
        directives: { perm: {} },
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('查询用户')
    expect(wrapper.text()).toContain('GET /users')
    expect(wrapper.findAllComponents({ name: 'ElOption' }).map((option) => option.props('value'))).toEqual([
      30002,
    ])

    vi.mocked(getPermissionApis).mockResolvedValue([])
    await wrapper.get('[data-testid="unbind-api"]').trigger('click')
    await flushPromises()

    expect(unbindApiFromPermission).toHaveBeenCalledWith(20001, 30001)
    expect(wrapper.text()).toContain('暂无已映射 API')
  })

  it('FR-19 可将尚未映射的 API 建立到当前权限', async () => {
    const wrapper = mount(ApiMappingCard, {
      props: {
        systemId: 1,
        permission,
      },
      global: {
        plugins: [ElementPlus],
        directives: { perm: {} },
      },
    })
    await flushPromises()

    ;(wrapper.findComponent('[data-testid="api-select"]') as VueWrapper).vm.$emit(
      'update:modelValue',
      30002,
    )
    await flushPromises()
    await wrapper.get('[data-testid="map-api"]').trigger('click')
    await flushPromises()

    expect(bindApiToPermission).toHaveBeenCalledWith(20001, 30002)
  })
})
