import { afterEach, describe, expect, it } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import request from '@/utils/request'
import { getPermissionApis, unbindApiFromPermission } from '@/api/system.api'
import type { Api } from '@/types/system.types'

const originalAdapter = request.defaults.adapter

afterEach(() => {
  request.defaults.adapter = originalAdapter
})

describe('FR-19 API 与前端权限映射', () => {
  it('FR-19 查询某个前端权限当前映射的 API', async () => {
    const mappedApi: Api = {
      id: 31001,
      systemId: 1,
      permissionId: 20001,
      apiCode: 'USER_LIST',
      apiName: '查询用户列表',
      apiPath: '/users',
      httpMethod: 'GET',
      status: 'ACTIVE',
      isDeleted: 0,
      createdAt: '2026-11-22T10:00:00',
      updatedAt: '2026-11-22T10:00:00',
    }
    const requests: InternalAxiosRequestConfig[] = []
    request.defaults.adapter = async (config) => {
      requests.push(config)
      return {
        data: { code: 0, message: 'success', data: [mappedApi], timestamp: '2026-11-22T20:00:00' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }

    await expect(getPermissionApis(20001)).resolves.toEqual([mappedApi])
    expect(requests[0]?.method).toBe('get')
    expect(requests[0]?.url).toBe('/permissions/20001/apis')
  })

  it('FR-19 解除不再生效的前端权限 API 映射', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    request.defaults.adapter = async (config) => {
      requests.push(config)
      return {
        data: { code: 0, message: 'success', data: null, timestamp: '2026-11-22T20:00:00' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }

    await unbindApiFromPermission(20001, 31001)

    expect(requests[0]?.method).toBe('delete')
    expect(requests[0]?.url).toBe('/permissions/20001/apis/31001')
  })
})
