import { afterEach, describe, expect, it } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import request from '@/utils/request'
import { listRoleAssignments } from '@/api/role.api'

const originalAdapter = request.defaults.adapter

afterEach(() => {
  request.defaults.adapter = originalAdapter
})

describe('FR-07 账号与角色绑定关系查询', () => {
  it('FR-07 按角色查询已授予用户列表', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    request.defaults.adapter = async (config) => {
      requests.push(config)
      return {
        data: {
          code: 0,
          message: 'success',
          data: [
            {
              id: 9001,
              userId: 16,
              roleId: 12,
              effectiveTime: '2026-11-22T10:00:00',
              status: 'ACTIVE',
              createdAt: '2026-11-22T10:00:00',
              updatedAt: '2026-11-22T10:00:00',
            },
          ],
          timestamp: '2026-11-22T20:00:00',
        },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }

    const result = await listRoleAssignments(12)

    expect(result.map((item) => item.userId)).toEqual([16])
    expect(requests[0]?.method).toBe('get')
    expect(requests[0]?.url).toBe('/roles/12/assignments')
  })
})
