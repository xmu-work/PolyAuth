<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Monitor, Plus } from '@element-plus/icons-vue'
import { listSystems } from '@/api/system.api'
import { useDict } from '@/composables/useDict'
import type { System } from '@/types/system.types'
import { ADMIN_ACTION_PERMISSIONS as FP } from '@/constants/permissions'

/** 接入系统列表卡：系统选择与登记入口。 */
const props = defineProps<{
  reloadSignal: number
  selectedSystemId: number | null
}>()

const emit = defineEmits<{
  create: []
  loaded: [systems: System[]]
  select: [systemId: number]
}>()

const { dictLabel, commonStatusOptions } = useDict()

const systems = ref<System[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    systems.value = await listSystems()
    emit('loaded', systems.value)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '系统列表加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

watch(
  () => props.reloadSignal,
  () => void load(),
)
</script>

<template>
  <el-card class="system-list-card" shadow="never">
    <template #header>
      <div class="system-list-card__header">
        <div>
          <h2 class="system-list-card__title">接入系统</h2>
          <p class="system-list-card__hint">共 {{ systems.length }} 个系统</p>
        </div>
        <el-button
          v-perm="FP.SYSTEM_CREATE"
          type="primary"
          plain
          size="small"
          :icon="Plus"
          @click="emit('create')"
        >
          登记
        </el-button>
      </div>
    </template>

    <el-alert
      v-if="error"
      class="system-list-card__error"
      type="error"
      :title="error"
      :closable="false"
      show-icon
    >
      <el-button size="small" type="primary" @click="load">重试</el-button>
    </el-alert>

    <div v-else v-loading="loading" class="system-list-card__items">
      <button
        v-for="system in systems"
        :key="system.id"
        type="button"
        class="system-item"
        :class="{ 'system-item--active': selectedSystemId === system.id }"
        @click="emit('select', system.id)"
      >
        <span class="system-item__icon">
          <Monitor />
        </span>
        <span class="system-item__body">
          <span class="system-item__name">{{ system.systemName }}</span>
          <code class="system-item__code">{{ system.systemCode }}</code>
          <span class="system-item__description">
            {{ system.description ?? '暂无描述' }}
          </span>
        </span>
        <el-tag
          size="small"
          :type="system.status === 'ACTIVE' ? 'success' : 'info'"
          effect="light"
        >
          {{ dictLabel(commonStatusOptions, system.status) }}
        </el-tag>
      </button>
      <el-empty
        v-if="systems.length === 0 && !loading"
        description="暂无接入系统"
        :image-size="72"
      />
    </div>
  </el-card>
</template>

<style scoped>
.system-list-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.system-list-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.system-list-card__title {
  margin: var(--space-0);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.system-list-card__hint {
  margin: var(--space-1) var(--space-0) var(--space-0);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.system-list-card__error {
  margin-bottom: var(--space-3);
}

.system-list-card__items {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.system-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3);
  border: var(--border-width-base) solid transparent;
  border-radius: var(--radius-lg);
  color: inherit;
  font: inherit;
  text-align: left;
  background: var(--color-bg-card);
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.system-item:hover {
  border-color: var(--color-primary-light-7);
  background: var(--color-bg-hover);
}

.system-item--active {
  border-color: var(--color-primary-light-7);
  background: var(--color-primary-light-9);
}

.system-item__icon {
  display: grid;
  flex: 0 0 var(--space-8);
  width: var(--space-8);
  height: var(--space-8);
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary);
  background: var(--color-primary-light-9);
}

.system-item--active .system-item__icon {
  color: var(--color-text-inverse);
  background: var(--color-primary);
}

.system-item__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.system-item__name {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-item__code {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.system-item__description {
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
