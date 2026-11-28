import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import OrgTreeCard from '@/views/organization/components/OrgTreeCard.vue'
import { getOrgChildren, getOrgTree, moveOrg } from '@/api/org.api'
import type { Org } from '@/types/org.types'

vi.mock('@/api/org.api', () => ({
  getOrg: vi.fn(),
  getOrgTree: vi.fn(),
  getOrgChildren: vi.fn(),
  moveOrg: vi.fn(),
}))

const hqOrg: Org = {
  id: 1,
  ancestors: '0',
  orgCode: 'HQ',
  orgName: '总公司',
  orgType: 'HQ',
  orgLevel: 1,
  sortNo: 0,
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const branchOrg: Org = {
  id: 100,
  parentId: 1,
  ancestors: '0,1',
  orgCode: 'BRANCH_01',
  orgName: '道路工程分公司',
  orgType: 'BRANCH',
  orgLevel: 2,
  sortNo: 1,
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

const deptOrg: Org = {
  id: 300,
  parentId: 100,
  ancestors: '0,1,100',
  orgCode: 'BRANCH_01_D01',
  orgName: '道路一处',
  orgType: 'DEPT',
  orgLevel: 3,
  sortNo: 1,
  status: 'ACTIVE',
  isDeleted: 0,
  createdAt: '2026-11-22T10:00:00',
  updatedAt: '2026-11-22T10:00:00',
}

function mountOrgTree() {
  return mount(OrgTreeCard, {
    props: {
      reloadSignal: 0,
      selectedOrgId: null,
    },
    global: {
      plugins: [ElementPlus],
      stubs: { teleport: true },
    },
  })
}

describe('组织结构树展开与收起', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(getOrgTree).mockReset()
    vi.mocked(getOrgChildren).mockReset()
    vi.mocked(moveOrg).mockReset()

    vi.mocked(getOrgTree).mockResolvedValue([hqOrg])
    vi.mocked(getOrgChildren).mockImplementation(async (parentId: number) => {
      if (parentId === 1) {
        return [branchOrg]
      }
      if (parentId === 100) {
        return [deptOrg]
      }
      return []
    })
  })

  it('展开一级与二级节点后，点击一级节点可正常收起', async () => {
    const wrapper = mountOrgTree()
    await flushPromises()

    await wrapper.get('[data-key="1"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-key="1"]').classes()).toContain('is-expanded')

    await wrapper.get('[data-key="100"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-key="100"]').classes()).toContain('is-expanded')

    await wrapper.get('[data-key="1"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-key="1"]').classes()).not.toContain('is-expanded')
  })

  it('点击三级叶子节点后，二级与一级节点仍可正常收起', async () => {
    const wrapper = mountOrgTree()
    await flushPromises()

    await wrapper.get('[data-key="1"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()
    await wrapper.get('[data-key="100"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()

    await wrapper.get('[data-key="300"] > .el-tree-node__content').trigger('click')
    await flushPromises()

    await wrapper.get('[data-key="100"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-key="100"]').classes()).not.toContain('is-expanded')

    await wrapper.get('[data-key="1"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-key="1"]').classes()).not.toContain('is-expanded')
  })

  it('收起子节点后点击刷新按钮，重建树仅保留仍处于展开态的父节点', async () => {
    const wrapper = mountOrgTree()
    await flushPromises()

    await wrapper.get('[data-key="1"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()
    await wrapper.get('[data-key="100"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()

    await wrapper.get('[data-key="100"] > .el-tree-node__content .el-tree-node__expand-icon').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-key="100"]').classes()).not.toContain('is-expanded')

    const refreshButton = wrapper.findAll('button').find((button) => button.text().includes('刷新'))
    expect(refreshButton).toBeDefined()
    await refreshButton?.trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-key="1"]').classes()).toContain('is-expanded')
    expect(wrapper.get('[data-key="100"]').classes()).not.toContain('is-expanded')
  })
})
