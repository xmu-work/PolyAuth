import { ref } from 'vue'
import { listPermissions } from '@/api/system.api'
import type { Permission, System } from '@/types/system.types'

/** 管理功能权限页当前系统与权限树的加载状态。 */
export function usePermissionCatalog() {
  const systems = ref<System[]>([])
  const selectedSystemId = ref<number | null>(null)
  const permissions = ref<Permission[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  let requestVersion = 0

  async function selectSystem(systemId: number): Promise<void> {
    const version = ++requestVersion
    selectedSystemId.value = systemId
    permissions.value = []
    loading.value = true
    error.value = null
    try {
      const list = await listPermissions(systemId)
      if (version === requestVersion) {
        permissions.value = list
      }
    } catch (caught) {
      if (version === requestVersion) {
        error.value = caught instanceof Error ? caught.message : '权限树加载失败'
      }
    } finally {
      if (version === requestVersion) {
        loading.value = false
      }
    }
  }

  return {
    systems,
    selectedSystemId,
    permissions,
    loading,
    error,
    selectSystem,
  }
}
