import { afterEach, describe, expect, it } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import request from '@/utils/request'
import { listDataScopeOrganizations } from '@/api/dataScope.api'

const originalAdapter = request.defaults.adapter

afterEach(() => {
  request.defaults.adapter = originalAdapter
})

describe('FR-22 数据权限范围', () => {
  it('FR-22 数据范围目标组织从当前组织根节点动态加载', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    const rootA = {
      id: 27,
      ancestors: '',
      orgCode: 'HQ-A',
      orgName: '总公司 A',
      orgType: 'HQ',
      orgLevel: 1,
      sortNo: 1,
      status: 'ACTIVE',
      isDeleted: 0,
      createdAt: '2026-11-22T10:00:00',
      updatedAt: '2026-11-22T10:00:00',
    }
    const rootB = {
      ...rootA,
      id: 99,
      orgCode: 'HQ-B',
      orgName: '总公司 B',
      sortNo: 2,
    }
    const branch = {
      ...rootA,
      id: 28,
      parentId: 27,
      orgCode: 'BRANCH-A',
      orgName: '分公司 A',
      orgType: 'BRANCH',
      orgLevel: 2,
    }
    const department = {
      ...rootA,
      id: 100,
      parentId: 99,
      orgCode: 'DEPT-B',
      orgName: '部门 B',
      orgType: 'DEPT',
      orgLevel: 3,
    }
    request.defaults.adapter = async (config) => {
      requests.push(config)
      let data = [rootA, rootB]
      if (config.params?.rootId === 27) {
        data = [rootA, branch]
      } else if (config.params?.rootId === 99) {
        data = [rootB, department]
      }
      return {
        data: {
          code: 0,
          message: 'success',
          data,
          timestamp: '2026-11-22T20:00:00',
        },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }

    const organizations = await listDataScopeOrganizations()

    expect(organizations.map((org) => org.id)).toEqual([27, 28, 99, 100])
    expect(requests.map((request) => request.params)).toEqual([
      undefined,
      { rootId: 27 },
      { rootId: 99 },
    ])
  })
})
