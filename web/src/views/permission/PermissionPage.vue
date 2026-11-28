<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  Connection,
  Grid,
  Monitor,
  Plus,
  Refresh,
  WarningFilled,
} from '@element-plus/icons-vue'
import SystemListCard from './components/SystemListCard.vue'
import SystemCreateDialog from './components/SystemCreateDialog.vue'
import PermissionTreeCard from './components/PermissionTreeCard.vue'
import PermissionRegisterCard from './components/PermissionRegisterCard.vue'
import ApiRegisterCard from './components/ApiRegisterCard.vue'
import ApiMappingCard from './components/ApiMappingCard.vue'
import { usePermissionCatalog } from '@/composables/usePermissionCatalog'
import { listApis } from '@/api/system.api'
import { ADMIN_ACTION_PERMISSIONS as FP } from '@/constants/permissions'
import type { System } from '@/types/system.types'

/** 功能权限管理主页：系统 / 权限树 / API 目录 / 角色授权治理。 */
const systems = ref<System[]>([])
const listReloadSignal = ref(0)
const apiReloadSignal = ref(0)
const selectedPermissionId = ref<number | null>(null)
const createDialogVisible = ref(false)
const permissionDialogVisible = ref(false)
const apiDialogVisible = ref(false)
const apiCount = ref(0)
const unmappedApiCount = ref(0)
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

const menuCount = computed(
  () => permissions.value.filter((permission) => permission.permissionType === 'MENU').length,
)
const buttonCount = computed(
  () => permissions.value.filter((permission) => permission.permissionType === 'BUTTON').length,
)
const mappedApiCount = computed(() => Math.max(apiCount.value - unmappedApiCount.value, 0))

interface StatTile {
  key: string
  label: string
  value: number | string
  hint: string
  icon: typeof Monitor
  tone: string
}

const statTiles = computed<StatTile[]>(() => [
  {
    key: 'systems',
    label: '接入系统',
    value: systems.value.length,
    hint: '统一纳管',
    icon: Monitor,
    tone: 'primary',
  },
  {
    key: 'permissions',
    label: '权限节点',
    value: permissions.value.length,
    hint: `${menuCount.value} 菜单 · ${buttonCount.value} 按钮`,
    icon: Grid,
    tone: 'violet',
  },
  {
    key: 'apis',
    label: '后端 API',
    value: apiCount.value,
    hint: `${mappedApiCount.value} 个已映射`,
    icon: Connection,
    tone: 'teal',
  },
  {
    key: 'unmapped',
    label: '未映射 API',
    value: unmappedApiCount.value,
    hint: unmappedApiCount.value > 0 ? '需要补充映射' : '全部已映射',
    icon: WarningFilled,
    tone: unmappedApiCount.value > 0 ? 'amber' : 'success',
  },
])

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

async function loadApiStats(systemId: number | null): Promise<void> {
  if (systemId === null) {
    apiCount.value = 0
    unmappedApiCount.value = 0
    return
  }
  try {
    const apis = await listApis(systemId)
    apiCount.value = apis.length
    unmappedApiCount.value = apis.filter((api) => api.permissionId == null).length
  } catch {
    apiCount.value = 0
    unmappedApiCount.value = 0
  }
}

watch(
  () => [selectedSystemId.value, apiReloadSignal.value] as const,
  ([systemId]) => void loadApiStats(systemId),
  { immediate: true },
)

function handleApiCreated(): void {
  apiReloadSignal.value += 1
}

function handlePermissionCreated(): void {
  permissionDialogVisible.value = false
  void reloadPermissions()
  apiReloadSignal.value += 1
}

function handleApiRegistered(): void {
  apiDialogVisible.value = false
  apiReloadSignal.value += 1
}

function refreshAll(): void {
  listReloadSignal.value += 1
  apiReloadSignal.value += 1
  void reloadPermissions()
}
</script>

<template>
  <div class="permission-page">
    <header class="permission-page__hero">
      <div class="permission-page__hero-copy">
        <span class="permission-page__eyebrow">权限治理</span>
        <h1 class="permission-page__title">功能权限</h1>
        <p class="permission-page__subtitle">
          统一维护接入系统、前端菜单/按钮权限树与后端 API 映射；未登记或未映射的接口默认拒绝。
        </p>
      </div>
      <div class="permission-page__hero-actions">
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
        <el-button
          v-perm="FP.PERMISSION_CREATE"
          :icon="Plus"
          plain
          type="primary"
          @click="permissionDialogVisible = true"
        >
          登记权限
        </el-button>
        <el-button
          v-perm="FP.API_CREATE"
          :icon="Plus"
          type="primary"
          @click="apiDialogVisible = true"
        >
          登记 API
        </el-button>
      </div>
    </header>

    <section class="permission-page__stats">
      <article
        v-for="tile in statTiles"
        :key="tile.key"
        class="permission-stat"
        :class="`permission-stat--${tile.tone}`"
      >
        <span class="permission-stat__icon">
          <component :is="tile.icon" />
        </span>
        <div class="permission-stat__body">
          <span class="permission-stat__label">{{ tile.label }}</span>
          <strong class="permission-stat__value">{{ tile.value }}</strong>
          <span class="permission-stat__hint">{{ tile.hint }}</span>
        </div>
      </article>
    </section>

    <div class="permission-page__grid">
      <aside class="permission-page__sidebar">
        <SystemListCard
          :reload-signal="listReloadSignal"
          :selected-system-id="selectedSystemId"
          @create="createDialogVisible = true"
          @loaded="handleSystemsLoaded"
          @select="handleSystemSelect"
        />
      </aside>

      <section class="permission-page__tree">
        <PermissionTreeCard
          :permissions="permissions"
          :selected-permission-id="selectedPermissionId"
          :loading="permissionsLoading"
          :error="permissionsError"
          @select="selectedPermissionId = $event"
        />
      </section>

      <section class="permission-page__mapping">
        <ApiMappingCard
          :system-id="selectedSystemId"
          :permission="selectedPermission"
          :reload-signal="apiReloadSignal"
          @changed="handleApiCreated"
        />
      </section>
    </div>

    <SystemCreateDialog v-model="createDialogVisible" @success="handleSystemCreated" />

    <el-dialog v-model="permissionDialogVisible" title="登记前端权限" width="620px" destroy-on-close>
      <PermissionRegisterCard
        embedded
        :systems="systems"
        :permissions="permissions"
        :selected-system-id="selectedSystemId"
        @created="handlePermissionCreated"
      />
    </el-dialog>

    <el-dialog v-model="apiDialogVisible" title="登记后端 API" width="620px" destroy-on-close>
      <ApiRegisterCard
        embedded
        :systems="systems"
        :selected-system-id="selectedSystemId"
        @created="handleApiRegistered"
      />
    </el-dialog>
  </div>
</template>

<style scoped>
.permission-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.permission-page__hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  border: var(--border-width-base) solid var(--color-border-light);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at 100% 0%, var(--color-primary-light-9), transparent 42%),
    var(--color-bg-card);
  box-shadow: var(--shadow-card);
}

.permission-page__hero-copy {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  max-width: 760px;
}

.permission-page__eyebrow {
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.12em;
}

.permission-page__title {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-2xl);
  font-weight: 700;
}

.permission-page__subtitle {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.permission-page__hero-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}

.permission-page__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.permission-stat {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border: var(--border-width-base) solid var(--color-border-light);
  border-radius: var(--radius-lg);
  background: var(--color-bg-card);
  box-shadow: var(--shadow-card);
}

.permission-stat__icon {
  display: grid;
  flex: 0 0 var(--space-10);
  width: var(--space-10);
  height: var(--space-10);
  place-items: center;
  border-radius: var(--radius-md);
  font-size: var(--font-size-lg);
}

.permission-stat__icon :deep(svg) {
  width: 1em;
  height: 1em;
}

.permission-stat--primary .permission-stat__icon {
  color: var(--color-primary);
  background: var(--color-primary-light-9);
}

.permission-stat--violet .permission-stat__icon {
  color: var(--color-accent-violet);
  background: var(--color-accent-violet-light);
}

.permission-stat--teal .permission-stat__icon {
  color: var(--color-accent-teal);
  background: var(--color-accent-teal-light);
}

.permission-stat--amber .permission-stat__icon {
  color: var(--color-warning);
  background: var(--color-warning-light-9);
}

.permission-stat--success .permission-stat__icon {
  color: var(--color-success);
  background: var(--color-success-light-9);
}

.permission-stat__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.permission-stat__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.permission-stat__value {
  color: var(--color-text-primary);
  font-size: var(--font-size-2xl);
  font-weight: 700;
  line-height: 1.1;
}

.permission-stat__hint {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.permission-page__grid {
  display: grid;
  grid-template-columns:
    minmax(230px, 0.68fr)
    minmax(420px, 1.28fr)
    minmax(360px, 1fr);
  gap: var(--space-4);
  align-items: start;
}

.permission-page__sidebar,
.permission-page__tree,
.permission-page__mapping {
  min-width: 0;
}

@media (width <= 1400px) {
  .permission-page__grid {
    grid-template-columns: minmax(240px, 0.8fr) minmax(0, 2fr);
  }

  .permission-page__mapping {
    grid-column: 1 / -1;
  }
}

@media (width <= 1200px) {
  .permission-page__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 1080px) {
  .permission-page__grid {
    grid-template-columns: 1fr;
  }

  .permission-page__mapping {
    grid-column: auto;
  }
}

@media (width <= 720px) {
  .permission-page__hero {
    flex-direction: column;
  }

  .permission-page__hero-actions {
    justify-content: flex-start;
  }

  .permission-page__stats {
    grid-template-columns: 1fr;
  }
}
</style>
