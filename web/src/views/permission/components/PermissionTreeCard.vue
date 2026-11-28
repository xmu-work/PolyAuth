<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Expand, Fold, Grid, Menu, Search } from '@element-plus/icons-vue'
import { OperationActionLabel, type OperationActionCode } from '@/constants/enums'
import { buildPermissionTree } from '@/utils/permissionTree'
import type { Permission } from '@/types/system.types'

type PermissionTypeFilter = 'ALL' | 'MENU' | 'BUTTON'

const props = defineProps<{
  permissions: Permission[]
  selectedPermissionId?: number | null
  loading?: boolean
  error?: string | null
}>()

const emit = defineEmits<{ select: [permissionId: number] }>()

const searchText = ref('')
const typeFilter = ref<PermissionTypeFilter>('ALL')
const expandAll = ref(false)
const treeKey = ref(0)

const menuCount = computed(
  () => props.permissions.filter((permission) => permission.permissionType === 'MENU').length,
)
const buttonCount = computed(
  () => props.permissions.filter((permission) => permission.permissionType === 'BUTTON').length,
)

/** 搜索/类型过滤后保留命中节点及其祖先，保证树结构完整。 */
const filteredPermissions = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  const byId = new Map(props.permissions.map((permission) => [permission.id, permission]))
  const keep = new Set<number>()

  for (const permission of props.permissions) {
    const typeMatched =
      typeFilter.value === 'ALL' || permission.permissionType === typeFilter.value
    const keywordMatched =
      keyword === ''
      || permission.permissionName.toLowerCase().includes(keyword)
      || permission.permissionCode.toLowerCase().includes(keyword)
    if (!typeMatched || !keywordMatched) {
      continue
    }
    keep.add(permission.id)
    let parentId = permission.parentId
    while (parentId !== undefined && parentId !== null && !keep.has(parentId)) {
      keep.add(parentId)
      parentId = byId.get(parentId)?.parentId
    }
  }

  return props.permissions.filter((permission) => keep.has(permission.id))
})

const tree = computed(() => buildPermissionTree(filteredPermissions.value))

/**
 * 树视口固定为“全部收起”时的高度；展开后内容在视口内滚动。
 * 根节点数量不随搜索变化，避免搜索时卡片高度跳动。
 */
const collapsedHeight = computed(() => {
  const rootCount = buildPermissionTree(props.permissions).length
  return Math.max(200, rootCount * 36 + 16)
})

watch([searchText, typeFilter], async ([keyword, type]) => {
  if (keyword.trim() !== '' || type !== 'ALL') {
    expandAll.value = true
    treeKey.value += 1
    await nextTick()
  }
})

function actionLabel(action: string): string {
  return OperationActionLabel[action as OperationActionCode] ?? action
}

function toggleExpand(): void {
  expandAll.value = !expandAll.value
  treeKey.value += 1
}

function resetFilters(): void {
  searchText.value = ''
  typeFilter.value = 'ALL'
}
</script>

<template>
  <el-card v-loading="loading" class="permission-tree-card" shadow="never">
    <template #header>
      <div class="permission-tree-card__header">
        <div>
          <h2 class="permission-tree-card__title">前端权限树</h2>
          <p class="permission-tree-card__hint">
            共 {{ permissions.length }} 个节点 · {{ menuCount }} 菜单 · {{ buttonCount }} 按钮
          </p>
        </div>
        <el-button link type="primary" :icon="expandAll ? Fold : Expand" @click="toggleExpand">
          {{ expandAll ? '收起全部' : '展开全部' }}
        </el-button>
      </div>
    </template>

    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />

    <template v-else>
      <div class="permission-tree-card__toolbar">
        <el-input
          v-model="searchText"
          clearable
          :prefix-icon="Search"
          placeholder="搜索权限名称或编码"
          class="permission-tree-card__search"
        />
        <el-select v-model="typeFilter" class="permission-tree-card__type-select">
          <el-option label="全部类型" value="ALL" />
          <el-option label="菜单" value="MENU" />
          <el-option label="按钮" value="BUTTON" />
        </el-select>
        <el-button plain @click="resetFilters">重置</el-button>
      </div>

      <el-empty
        v-if="permissions.length === 0"
        description="当前系统暂无权限节点"
        :image-size="90"
      />
      <el-empty
        v-else-if="tree.length === 0"
        description="没有匹配的权限节点"
        :image-size="90"
      />
      <div
        v-else
        class="permission-tree-card__viewport"
        :style="{ height: `${collapsedHeight}px` }"
      >
        <el-tree
          :key="treeKey"
          :data="tree"
          node-key="id"
          :default-expand-all="expandAll"
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
              <span
                class="permission-tree-card__node-icon"
                :class="data.permissionType === 'MENU'
                  ? 'permission-tree-card__node-icon--menu'
                  : 'permission-tree-card__node-icon--button'"
              >
                <Menu v-if="data.permissionType === 'MENU'" />
                <Grid v-else />
              </span>
              <span class="permission-tree-card__name">{{ data.permissionName }}</span>
              <code class="permission-tree-card__code">{{ data.permissionCode }}</code>
              <el-tag size="small" type="info" effect="light">
                {{ actionLabel(data.action) }}
              </el-tag>
              <el-tag size="small" effect="plain">
                {{ data.permissionType === 'MENU' ? '菜单' : '按钮' }}
              </el-tag>
            </div>
          </template>
        </el-tree>
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.permission-tree-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.permission-tree-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.permission-tree-card__title {
  margin: var(--space-0);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.permission-tree-card__hint {
  margin: var(--space-1) var(--space-0) var(--space-0);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.permission-tree-card__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.permission-tree-card__search {
  flex: 1;
  min-width: 180px;
}

.permission-tree-card__type-select {
  width: 120px;
}

.permission-tree-card__viewport {
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: var(--space-1);
  scrollbar-color: var(--color-border-base) transparent;
  scrollbar-width: thin;
}

.permission-tree-card__viewport::-webkit-scrollbar {
  width: 6px;
}

.permission-tree-card__viewport::-webkit-scrollbar-thumb {
  border-radius: var(--radius-round);
  background: var(--color-border-base);
}

.permission-tree-card__viewport::-webkit-scrollbar-track {
  background: transparent;
}

.permission-tree-card__node {
  display: flex;
  flex: 1;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  padding: var(--space-1) var(--space-2) var(--space-1) var(--space-0);
  cursor: pointer;
}

.permission-tree-card__node-icon {
  display: grid;
  flex: 0 0 var(--space-6);
  width: var(--space-6);
  height: var(--space-6);
  place-items: center;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
}

.permission-tree-card__node-icon :deep(svg) {
  width: 0.9em;
  height: 0.9em;
}

.permission-tree-card__node-icon--menu {
  color: var(--color-primary);
  background: var(--color-primary-light-9);
}

.permission-tree-card__node-icon--button {
  color: var(--color-accent-teal);
  background: var(--color-accent-teal-light);
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
