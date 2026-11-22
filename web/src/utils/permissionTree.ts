import type { Permission } from '@/types/system.types'

export interface PermissionTreeNode extends Permission {
  children: PermissionTreeNode[]
}

/** 将后端返回的扁平权限列表组装成前端权限树。 */
export function buildPermissionTree(permissions: Permission[]): PermissionTreeNode[] {
  const nodes = new Map<number, PermissionTreeNode>()
  permissions.forEach((permission) => {
    nodes.set(permission.id, { ...permission, children: [] })
  })

  const roots: PermissionTreeNode[] = []
  nodes.forEach((node) => {
    const parent = node.parentId == null ? undefined : nodes.get(node.parentId)
    if (parent) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })

  const bySort = (left: PermissionTreeNode, right: PermissionTreeNode): number =>
    left.sortNo - right.sortNo || left.id - right.id

  const sortBranch = (branch: PermissionTreeNode[]): void => {
    branch.sort(bySort)
    branch.forEach((node) => sortBranch(node.children))
  }
  sortBranch(roots)
  return roots
}
