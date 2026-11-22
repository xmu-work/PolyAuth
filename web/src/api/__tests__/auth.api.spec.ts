import { afterEach, describe, expect, it } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import request from '@/utils/request'
import { changePassword } from '@/api/auth.api'

const originalAdapter = request.defaults.adapter

afterEach(() => {
  request.defaults.adapter = originalAdapter
})

describe('FR-34 自助修改密码', () => {
  it('FR-34 修改密码按后端契约发送原密码和新密码', async () => {
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

    await changePassword({
      oldPassword: 'Admin123',
      newPassword: 'Changed456',
    })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('put')
    expect(requests[0]?.url).toBe('/auth/password')
    expect(requests[0]?.params).toEqual({
      oldPassword: 'Admin123',
      newPassword: 'Changed456',
    })
  })
})
