<script setup lang="ts">
import { computed } from 'vue'
import { OperationActionLabel, type OperationActionCode } from '@/constants/enums'
import { buildPermissionTree } from '@/utils/permissionTree'
import type { Permission } from '@/types/system.types'

const props = defineProps<{
  permissions: Permission[]
  selectedPermissionId?: number | null
  loading?: boolean
  error?: string | null
}>()

const emit = defineEmits<{ select: [permissionId: number] }>()

const tree = computed(() => buildPermissionTree(props.permissions))

function actionLabel(action: string): string {
  return OperationActionLabel[action as OperationActionCode] ?? action
}
</script>

<template>
  <el-card v-loading="loading" class="permission-tree-card" shadow="never">
    <template #header>
      <div>
        <h2 class="permission-tree-card__title">前端权限树</h2>
        <p class="permission-tree-card__hint">点击节点选择权限；父子关系来自后端 parentId</p>
      </div>
    </template>
    <el-alert
      v-if="error"
      type="error"
      :title="error"
      :closable="false"
      show-icon
    />
    <el-empty v-else-if="permissions.length === 0" description="当前系统暂无权限节点" :image-size="90" />
    <el-tree
      v-else
      :data="tree"
      node-key="id"
      default-expand-all
      highlight-current
      :current-node-key="selectedPermissionId ?? undefined"
      :props="{ label: 'permissionName', children: 'children' }"
    >
      <template #default="{ data }">
        <div
          class="permission-tree-card__node"
          :data-permission-id="data.id"
          @click.stop="emit('select', data.id)"
        >
          <span class="permission-tree-card__name">{{ data.permissionName }}</span>
          <code class="permission-tree-card__code">{{ data.permissionCode }}</code>
          <el-tag size="small" effect="plain">{{ data.permissionType === 'MENU' ? '菜单' : '按钮' }}</el-tag>
          <el-tag size="small" type="info" effect="light">{{ actionLabel(data.action) }}</el-tag>
        </div>
      </template>
    </el-tree>
  </el-card>
</template>

<style scoped>
.permission-tree-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.permission-tree-card__title {
  margin: var(--space-0);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.permission-tree-card__hint {
  margin: var(--space-2) var(--space-0) var(--space-0);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.permission-tree-card__node {
  display: flex;
  flex: 1;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  padding-right: var(--space-2);
  cursor: pointer;
}

.permission-tree-card__name {
  overflow: hidden;
  color: var(--color-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-tree-card__code {
  overflow: hidden;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
