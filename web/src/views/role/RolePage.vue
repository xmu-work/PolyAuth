<script setup lang="ts">
import { computed, ref } from 'vue'
import { ADMIN_ACTION_PERMISSIONS as FP } from '@/constants/permissions'
import { usePermissionStore } from '@/stores/permission.store'
import RoleListCard from './components/RoleListCard.vue'
import RoleDetailCard from './components/RoleDetailCard.vue'
import RoleCreateDialog from './components/RoleCreateDialog.vue'
import RoleGovernanceCard from './components/RoleGovernanceCard.vue'
import RoleMembersCard from './components/RoleMembersCard.vue'
import RolePermissionBindCard from './components/RolePermissionBindCard.vue'

/** 角色管理：列表页 → 详情页；详情页的扩展能力通过下拉菜单按需展示。 */
type RolePanel = 'permissions' | 'members' | 'governance'

const mode = ref<'list' | 'detail'>('list')
const selectedRoleId = ref<number | null>(null)
const activePanel = ref<RolePanel | ''>('')
const listReloadSignal = ref(0)
const detailReloadSignal = ref(0)
const createDialogVisible = ref(false)

const permissionStore = usePermissionStore()
const panelOptions = computed(() =>
  [
    { value: 'permissions' as const, label: '角色权限', permissionId: FP.ROLE_PERMISSION_LIST },
    { value: 'members' as const, label: '当前授权（角色成员）', permissionId: FP.ROLE_ASSIGN_LIST },
    { value: 'governance' as const, label: '继承、启停与互斥', permissionId: FP.ROLE_UPDATE },
  ].filter((option) => permissionStore.canAccess(option.permissionId)),
)

function handleDetail(roleId: number): void {
  selectedRoleId.value = roleId
  activePanel.value = ''
  mode.value = 'detail'
}

function handleBack(): void {
  mode.value = 'list'
  selectedRoleId.value = null
  activePanel.value = ''
  listReloadSignal.value += 1
}

function handleRoleCreated(): void {
  listReloadSignal.value += 1
  detailReloadSignal.value += 1
}
</script>

<template>
  <div class="role-page">
    <template v-if="mode === 'list'">
      <RoleListCard
        :reload-signal="listReloadSignal"
        @detail="handleDetail"
        @create="createDialogVisible = true"
      />
    </template>

    <template v-else>
      <el-page-header class="role-page__header" title="返回角色列表" @back="handleBack">
        <template #content>
          <span class="role-page__header-title">角色详情</span>
        </template>
      </el-page-header>

      <RoleDetailCard :role-id="selectedRoleId" :reload-signal="detailReloadSignal" />

      <el-card class="role-page__panel-card" shadow="never">
        <div class="role-page__panel-header">
          <div>
            <h2 class="role-page__panel-title">扩展功能</h2>
            <p class="role-page__panel-hint">选择要查看的功能，按需展示</p>
          </div>
          <el-select
            v-model="activePanel"
            clearable
            placeholder="选择要查看的功能"
            class="role-page__panel-select"
          >
            <el-option
              v-for="option in panelOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
        <el-empty
          v-if="activePanel === ''"
          description="请选择要查看的功能"
          :image-size="72"
        />
      </el-card>

      <RolePermissionBindCard v-if="activePanel === 'permissions'" :role-id="selectedRoleId" />
      <RoleMembersCard
        v-else-if="activePanel === 'members'"
        :role-id="selectedRoleId"
        :reload-signal="listReloadSignal"
      />
      <RoleGovernanceCard
        v-else-if="activePanel === 'governance'"
        :role-id="selectedRoleId"
        @updated="handleRoleCreated"
      />
    </template>

    <RoleCreateDialog v-model="createDialogVisible" @success="handleRoleCreated" />
  </div>
</template>

<style scoped>
.role-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 100%;
  max-width: 1120px;
}

.role-page__header {
  padding: var(--space-2) var(--space-0);
}

.role-page__header-title {
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.role-page__panel-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.role-page__panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.role-page__panel-title {
  margin: var(--space-0);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.role-page__panel-hint {
  margin: var(--space-2) var(--space-0) var(--space-0);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.role-page__panel-select {
  width: 280px;
}

@media (width <= 1080px) {
  .role-page__panel-header {
    flex-direction: column;
  }

  .role-page__panel-select {
    width: 100%;
  }
}
</style>
