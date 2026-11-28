<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { listRoles } from '@/api/role.api'
import { getRolePermissions, listPermissions, listSystems } from '@/api/system.api'
import type { Role } from '@/types/role.types'
import type { Permission, System } from '@/types/system.types'

/** 所有角色内置、不在此查看的权限编码 */
const BUILTIN_HIDDEN_CODES = new Set(['DATA_VIEW'])

const props = defineProps<{ roleId: number | null }>()

interface EffectiveBinding {
  permissionId: number
  sourceRoles: string[]
  direct: boolean
}

const systems = ref<System[]>([])
const permissions = ref<Permission[]>([])
const selectedSystemId = ref<number | null>(null)
const bindings = ref<EffectiveBinding[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const permissionMap = computed(
  () => new Map(permissions.value.map((permission) => [permission.id, permission])),
)

/** 当前系统下、该角色及其父角色实际拥有的权限（排除内置数据查看）。 */
const ownedPermissions = computed(() => {
  const bindingMap = new Map(bindings.value.map((binding) => [binding.permissionId, binding]))
  return permissions.value
    .filter(
      (permission) =>
        permission.systemId === selectedSystemId.value
        && bindingMap.has(permission.id)
        && !BUILTIN_HIDDEN_CODES.has(permission.permissionCode),
    )
    .sort((left, right) => left.sortNo - right.sortNo || left.id - right.id)
    .map((permission) => ({
      permission,
      sourceRoles: bindingMap.get(permission.id)?.sourceRoles ?? [],
      direct: bindingMap.get(permission.id)?.direct ?? false,
    }))
})

const onlyBuiltinPermissions = computed(
  () => bindings.value.length > 0 && ownedPermissions.value.length === 0,
)

onMounted(async () => {
  try {
    const systemList = await listSystems()
    systems.value = systemList
    const lists = await Promise.all(systemList.map((system) => listPermissions(system.id)))
    permissions.value = lists.flat()
    selectedSystemId.value = systemList[0]?.id ?? null
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '权限目录加载失败'
  }
  await loadBindings()
})

watch(
  () => props.roleId,
  () => void loadBindings(),
  { immediate: true },
)

async function loadBindings(): Promise<void> {
  if (props.roleId === null) {
    bindings.value = []
    return
  }
  loading.value = true
  error.value = null
  try {
    const roles = await listRoles()
    const roleMap = new Map(roles.map((role) => [role.id, role]))
    const effectiveRoles = collectEffectiveRoles(props.roleId, roleMap)
    const permissionLists = await Promise.all(
      effectiveRoles.map((entry) => getRolePermissions(entry.role.id)),
    )
    const merged = new Map<number, EffectiveBinding>()
    effectiveRoles.forEach((entry, index) => {
      for (const binding of permissionLists[index] ?? []) {
        const existing = merged.get(binding.permissionId)
        if (existing) {
          if (!existing.sourceRoles.includes(entry.role.roleName)) {
            existing.sourceRoles.push(entry.role.roleName)
          }
          existing.direct ||= entry.direct
        } else {
          merged.set(binding.permissionId, {
            permissionId: binding.permissionId,
            sourceRoles: [entry.role.roleName],
            direct: entry.direct,
          })
        }
      }
    })
    bindings.value = [...merged.values()]
  } catch (caught) {
    bindings.value = []
    error.value = caught instanceof Error ? caught.message : '角色权限加载失败'
  } finally {
    loading.value = false
  }
}

/** 自身 + 全部父角色（多继承），父角色不可用时停止该分支。 */
function collectEffectiveRoles(roleId: number, roleMap: Map<number, Role>): Array<{ role: Role; direct: boolean }> {
  const result: Array<{ role: Role; direct: boolean }> = []
  const visited = new Set<number>()
  const queue: Array<{ id: number; direct: boolean }> = [{ id: roleId, direct: true }]
  while (queue.length > 0) {
    const current = queue.shift()
    if (!current || visited.has(current.id)) {
      continue
    }
    visited.add(current.id)
    const role = roleMap.get(current.id)
    if (!role) {
      continue
    }
    result.push({ role, direct: current.direct })
    const parentIds = role.parentIds ?? (role.parentId === undefined ? [] : [role.parentId])
    parentIds.forEach((parentId) => queue.push({ id: parentId, direct: false }))
  }
  return result
}

function permissionTypeLabel(type: Permission['permissionType']): string {
  return type === 'MENU' ? '菜单' : '按钮'
}

function parentName(permission: Permission): string {
  if (permission.parentId === undefined || permission.parentId === null) {
    return '—'
  }
  return permissionMap.value.get(permission.parentId)?.permissionName ?? '—'
}
</script>

<template>
  <el-card v-loading="loading" class="role-permission-card" shadow="never">
    <template #header>
      <div>
        <h2 class="role-permission-card__title">角色权限</h2>
        <p class="role-permission-card__hint">
          选择系统查看该角色及其父角色继承的权限；数据查看为所有角色内置，不在此展示
        </p>
      </div>
    </template>

    <el-empty v-if="roleId === null" description="请先在角色列表中选择角色" :image-size="72" />
    <template v-else>
      <el-alert
        v-if="error"
        class="role-permission-card__error"
        type="error"
        :title="error"
        :closable="false"
        show-icon
      />
      <el-form label-position="top">
        <el-form-item label="系统">
          <el-select v-model="selectedSystemId" style="width: 100%">
            <el-option
              v-for="system in systems"
              :key="system.id"
              :label="system.systemName"
              :value="system.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-empty
        v-if="onlyBuiltinPermissions"
        description="该角色及父角色仅有内置数据查看权限，没有可展示的菜单/按钮权限"
        :image-size="72"
      />
      <el-empty
        v-else-if="ownedPermissions.length === 0"
        description="该角色及父角色在当前系统暂无权限"
        :image-size="72"
      />
      <el-table v-else :data="ownedPermissions" size="small" border>
        <el-table-column label="权限名称" min-width="160">
          <template #default="{ row }">
            {{ row.permission.permissionName }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">
              {{ permissionTypeLabel(row.permission.permissionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属菜单" min-width="130">
          <template #default="{ row }">{{ parentName(row.permission) }}</template>
        </el-table-column>
        <el-table-column label="来源角色" min-width="150">
          <template #default="{ row }">
            <el-tag
              v-for="roleName in row.sourceRoles"
              :key="roleName"
              size="small"
              type="info"
              effect="plain"
              class="role-permission-card__source"
            >
              {{ roleName }}
            </el-tag>
            <el-tag v-if="!row.direct" size="small" type="warning" effect="light">
              继承
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </el-card>
</template>

<style scoped>
.role-permission-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.role-permission-card__title {
  margin: var(--space-0);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.role-permission-card__hint {
  margin: var(--space-1) var(--space-0) var(--space-0);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.role-permission-card__error {
  margin-bottom: var(--space-3);
}

.role-permission-card__source {
  margin-right: var(--space-1);
}
</style>
