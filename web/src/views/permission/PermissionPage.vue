<script setup lang="ts">
import { computed, ref } from 'vue'
import SystemListCard from './components/SystemListCard.vue'
import SystemCreateDialog from './components/SystemCreateDialog.vue'
import PermissionTreeCard from './components/PermissionTreeCard.vue'
import PermissionRegisterCard from './components/PermissionRegisterCard.vue'
import ApiRegisterCard from './components/ApiRegisterCard.vue'
import ApiMappingCard from './components/ApiMappingCard.vue'
import RolePermissionBindCard from './components/RolePermissionBindCard.vue'
import { usePermissionCatalog } from '@/composables/usePermissionCatalog'
import type { System } from '@/types/system.types'

/** 功能权限管理主页：多系统、前端权限树、后端 API 与角色授权。 */
const systems = ref<System[]>([])
const listReloadSignal = ref(0)
const apiReloadSignal = ref(0)
const selectedPermissionId = ref<number | null>(null)
const createDialogVisible = ref(false)
const {
  selectedSystemId,
  permissions,
  loading: permissionsLoading,
  error: permissionsError,
  selectSystem,
} = usePermissionCatalog()

const selectedPermission = computed(
  () => permissions.value.find((permission) => permission.id === selectedPermissionId.value) ?? null,
)

async function handleSystemsLoaded(list: System[]): Promise<void> {
  systems.value = list
  if (selectedSystemId.value === null && list[0]) {
    await selectSystem(list[0].id)
  }
}

function handleSystemCreated(): void {
  listReloadSignal.value += 1
}

async function handleSystemSelect(systemId: number): Promise<void> {
  if (selectedSystemId.value === systemId) {
    return
  }
  selectedPermissionId.value = null
  await selectSystem(systemId)
}

async function reloadPermissions(): Promise<void> {
  if (selectedSystemId.value !== null) {
    await selectSystem(selectedSystemId.value)
  }
}

function handleApiCreated(): void {
  apiReloadSignal.value += 1
}
</script>

<template>
  <!-- 纯布局容器（契约 9.1-1）：只负责 grid 划分与间距 -->
  <div class="permission-page">
    <SystemListCard
      :reload-signal="listReloadSignal"
      :selected-system-id="selectedSystemId"
      @create="createDialogVisible = true"
      @loaded="handleSystemsLoaded"
      @select="handleSystemSelect"
    />
    <div class="permission-page__grid">
      <div class="permission-page__column">
        <PermissionTreeCard
          :permissions="permissions"
          :selected-permission-id="selectedPermissionId"
          :loading="permissionsLoading"
          :error="permissionsError"
          @select="selectedPermissionId = $event"
        />
        <PermissionRegisterCard
          :systems="systems"
          :permissions="permissions"
          :selected-system-id="selectedSystemId"
          @created="reloadPermissions"
        />
      </div>
      <div class="permission-page__column">
        <ApiRegisterCard
          :systems="systems"
          :selected-system-id="selectedSystemId"
          @created="handleApiCreated"
        />
        <ApiMappingCard
          :system-id="selectedSystemId"
          :permission="selectedPermission"
          :reload-signal="apiReloadSignal"
        />
        <RolePermissionBindCard :permissions="permissions" />
      </div>
    </div>
    <SystemCreateDialog v-model="createDialogVisible" @success="handleSystemCreated" />
  </div>
</template>

<style scoped>
.permission-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.permission-page__grid {
  display: grid;
  grid-template-columns: minmax(340px, 1fr) minmax(340px, 1fr);
  gap: var(--space-4);
  align-items: start;
}

.permission-page__column {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

@media (width <= 1080px) {
  .permission-page__grid {
    grid-template-columns: 1fr;
  }
}
</style>
