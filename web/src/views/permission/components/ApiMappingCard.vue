<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  bindApiToPermission,
  getPermissionApis,
  listApis,
  unbindApiFromPermission,
} from '@/api/system.api'
import { ADMIN_ACTION_PERMISSIONS as FP } from '@/constants/permissions'
import type { Api, Permission } from '@/types/system.types'

const props = defineProps<{
  systemId: number | null
  permission: Permission | null
  reloadSignal?: number
}>()

const loading = ref(false)
const submitting = ref(false)
const mappedApis = ref<Api[]>([])
const systemApis = ref<Api[]>([])
const selectedApiId = ref<number | undefined>(undefined)

const availableApis = computed(() =>
  systemApis.value.filter(
    (api) => api.permissionId == null && api.isDeleted === 0 && api.status === 'ACTIVE',
  ),
)

watch(
  () => [props.systemId, props.permission?.id, props.reloadSignal] as const,
  () => void load(),
  { immediate: true },
)

async function load(): Promise<void> {
  selectedApiId.value = undefined
  if (props.systemId === null) {
    mappedApis.value = []
    systemApis.value = []
    return
  }
  loading.value = true
  try {
    const [apis, mapped] = await Promise.all([
      listApis(props.systemId),
      props.permission === null ? Promise.resolve([]) : getPermissionApis(props.permission.id),
    ])
    systemApis.value = apis
    mappedApis.value = mapped
  } finally {
    loading.value = false
  }
}

async function bindSelectedApi(): Promise<void> {
  if (props.permission === null || selectedApiId.value === undefined) {
    return
  }
  submitting.value = true
  try {
    await bindApiToPermission(props.permission.id, selectedApiId.value)
    ElMessage.success('API 映射已建立')
    await load()
  } finally {
    submitting.value = false
  }
}

async function unbind(api: Api): Promise<void> {
  if (props.permission === null) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `解除「${api.apiName}」与当前权限的映射？解除后该 API 默认拒绝访问。`,
      '解除 API 映射',
      {
        confirmButtonText: '解除映射',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    await unbindApiFromPermission(props.permission.id, api.id)
    ElMessage.success('API 映射已解除')
    await load()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card v-loading="loading" class="api-mapping-card" shadow="never">
    <template #header>
      <div>
        <h2 class="api-mapping-card__title">API 映射</h2>
        <p class="api-mapping-card__hint">
          {{ permission ? `当前权限：${permission.permissionName}（#${permission.id}）` : '先在权限树中选择一个菜单或按钮' }}
        </p>
      </div>
    </template>

    <el-empty v-if="permission === null" description="请选择权限后管理 API 映射" :image-size="90" />
    <template v-else>
      <div class="api-mapping-card__toolbar">
        <el-select
          v-model="selectedApiId"
          data-testid="api-select"
          filterable
          clearable
          placeholder="选择未映射的 API"
          class="api-mapping-card__select"
        >
          <el-option
            v-for="api in availableApis"
            :key="api.id"
            :label="`${api.httpMethod} ${api.apiPath} · ${api.apiName}`"
            :value="api.id"
          />
        </el-select>
        <el-button
          v-perm.disable="FP.API_PERMISSION_BIND"
          data-testid="map-api"
          type="primary"
          :loading="submitting"
          :disabled="selectedApiId === undefined"
          @click="bindSelectedApi"
        >
          建立映射
        </el-button>
      </div>

      <div v-if="mappedApis.length === 0" class="api-mapping-card__empty">暂无已映射 API</div>
      <div v-else class="api-mapping-card__list">
        <div v-for="api in mappedApis" :key="api.id" class="api-mapping-card__row">
          <div class="api-mapping-card__api">
            <el-tag size="small" effect="plain">{{ api.httpMethod }}</el-tag>
            <code>{{ api.httpMethod }} {{ api.apiPath }}</code>
            <span>{{ api.apiName }}</span>
          </div>
          <el-button
            v-perm.disable="FP.API_PERMISSION_UNBIND"
            data-testid="unbind-api"
            link
            type="danger"
            :loading="submitting"
            @click="unbind(api)"
          >
            解除映射
          </el-button>
        </div>
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.api-mapping-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.api-mapping-card__title {
  margin: var(--space-0);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.api-mapping-card__hint {
  margin: var(--space-2) var(--space-0) var(--space-0);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.api-mapping-card__toolbar {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.api-mapping-card__select {
  flex: 1;
}

.api-mapping-card__empty {
  padding: var(--space-5);
  color: var(--color-text-placeholder);
  text-align: center;
}

.api-mapping-card__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.api-mapping-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3);
  border: var(--border-width-base) solid var(--color-border-light);
  border-radius: var(--radius-md);
}

.api-mapping-card__api {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.api-mapping-card__api code,
.api-mapping-card__api span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
