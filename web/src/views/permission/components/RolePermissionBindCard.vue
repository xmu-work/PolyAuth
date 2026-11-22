<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listRoles } from '@/api/role.api'
import { bindRolePermissions, getRolePermissions, unbindRolePermissions } from '@/api/system.api'
import {
  OperationAction,
  OperationActionLabel,
  type OperationActionCode,
} from '@/constants/enums'
import { ADMIN_ACTION_PERMISSIONS as FP } from '@/constants/permissions'
import { buildPermissionTree } from '@/utils/permissionTree'
import type { Role } from '@/types/role.types'
import type { Permission, RolePermission } from '@/types/system.types'

const props = defineProps<{ permissions: Permission[] }>()

const treeRef = ref<{ setCheckedKeys: (keys: number[]) => void }>()
const roles = ref<Role[]>([])
const roleId = ref<number>()
const selectedPermissionIds = ref<number[]>([])
const allowedAction = ref<OperationActionCode>(OperationAction.VIEW)
const bindings = ref<RolePermission[]>([])
const loading = ref(false)

const tree = computed(() => buildPermissionTree(props.permissions))
const permissionNames = computed(
  () => new Map(props.permissions.map((permission) => [permission.id, permission.permissionName])),
)
const actionOptions = Object.values(OperationAction).map((value) => ({
  value,
  label: OperationActionLabel[value],
}))

watch(roleId, () => void loadBindings())

onMounted(async () => {
  roles.value = await listRoles()
})

function handlePermissionCheck(
  _permission: Permission,
  state: { checkedKeys: Array<string | number> },
): void {
  selectedPermissionIds.value = state.checkedKeys.map(Number).filter((id) => Number.isSafeInteger(id))
}

async function loadBindings(): Promise<void> {
  if (roleId.value === undefined) {
    bindings.value = []
    selectedPermissionIds.value = []
    treeRef.value?.setCheckedKeys([])
    return
  }
  loading.value = true
  try {
    bindings.value = await getRolePermissions(roleId.value)
    const checkedKeys = bindings.value.map((binding) => binding.permissionId)
    selectedPermissionIds.value = checkedKeys
    treeRef.value?.setCheckedKeys(checkedKeys)
  } finally {
    loading.value = false
  }
}

async function bind(): Promise<void> {
  const ids = [...new Set(selectedPermissionIds.value)].sort((left, right) => left - right)
  if (roleId.value === undefined || ids.length === 0) {
    ElMessage.warning('请选择角色和至少一项权限')
    return
  }
  await bindRolePermissions({ roleId: roleId.value, permissionIds: ids, allowedAction: allowedAction.value })
  ElMessage.success(`已授予 ${ids.length} 项前端权限`)
  await loadBindings()
}

async function unbind(item: RolePermission): Promise<void> {
  if (roleId.value === undefined) return
  await unbindRolePermissions(roleId.value, [item.permissionId])
  ElMessage.success(`已解除权限 ${item.permissionId}`)
  await loadBindings()
}

function asRolePermission(row: unknown): RolePermission {
  return row as RolePermission
}

function permissionName(permissionId: number): string {
  return permissionNames.value.get(permissionId) ?? `权限 #${permissionId}`
}
</script>

<template>
  <el-card v-loading="loading" class="role-permission-card" shadow="never">
    <template #header>
      <div>
        <h2 class="role-permission-card__title">角色权限</h2>
        <p class="role-permission-card__hint">
          选择角色与权限节点后批量授予；菜单/按钮对应的 API 由映射关系独立校验
        </p>
      </div>
    </template>
    <el-form label-position="top">
      <el-form-item label="角色">
        <el-select v-model="roleId" filterable clearable placeholder="请选择角色" style="width: 100%">
          <el-option
            v-for="role in roles"
            :key="role.id"
            :label="`${role.roleCode} · ${role.roleName}`"
            :value="role.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="权限">
        <div class="role-permission-card__tree">
          <el-tree
            v-if="tree.length > 0"
            ref="treeRef"
            :data="tree"
            node-key="id"
            show-checkbox
            default-expand-all
            :props="{ label: 'permissionName', children: 'children' }"
            @check="handlePermissionCheck"
          />
          <el-empty v-else description="当前系统暂无权限节点" :image-size="72" />
        </div>
      </el-form-item>
      <el-form-item label="操作码">
        <el-select v-model="allowedAction" style="width: 100%">
          <el-option
            v-for="option in actionOptions"
            :key="option.value"
            :label="`${option.label}（${option.value}）`"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-button
        v-perm.disable="FP.ROLE_PERMISSION_BIND"
        data-testid="bind-permissions"
        type="primary"
        :loading="loading"
        @click="bind"
      >
        授予所选权限
      </el-button>
    </el-form>

    <el-divider />
    <h3 class="role-permission-card__subtitle">当前授权</h3>
    <el-empty
      v-if="roleId !== undefined && bindings.length === 0"
      description="该角色暂无前端权限"
      :image-size="72"
    />
    <el-table v-else :data="bindings" size="small">
      <el-table-column label="权限" min-width="180">
        <template #default="{ row }">{{ permissionName(asRolePermission(row).permissionId) }}</template>
      </el-table-column>
      <el-table-column prop="allowedAction" label="操作码" width="130" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button
            v-perm.disable="FP.ROLE_PERMISSION_UNBIND"
            link
            type="danger"
            @click="unbind(asRolePermission(row))"
          >
            解除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.role-permission-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.role-permission-card__title,
.role-permission-card__subtitle {
  margin: var(--space-0);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.role-permission-card__subtitle {
  font-size: var(--font-size-base);
}

.role-permission-card__hint {
  margin: var(--space-2) var(--space-0) var(--space-0);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.role-permission-card__tree {
  max-height: var(--layout-content-min-height);
  width: 100%;
  overflow-y: auto;
  padding: var(--space-2);
  border: var(--border-width-base) solid var(--color-border-light);
  border-radius: var(--radius-md);
}
</style>
