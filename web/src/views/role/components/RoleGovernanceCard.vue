<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  bindRoleExclusion,
  getRole,
  listRoleExclusions,
  listRoles,
  unbindRoleExclusion,
  updateRole,
} from '@/api/role.api'
import { ADMIN_ACTION_PERMISSIONS as FP } from '@/constants/permissions'
import type { Role } from '@/types/role.types'

/** RBAC1/RBAC3 角色治理：父角色、启停与静态互斥配置。 */
const props = defineProps<{ roleId: number | null }>()
const emit = defineEmits<{ updated: [] }>()

const roles = ref<Role[]>([])
const exclusions = ref<number[]>([])
const peerRoleId = ref<number | undefined>(undefined)
const loading = ref(false)
const submitting = ref(false)
const form = reactive<{
  roleName: string
  description: string
  status: 'ACTIVE' | 'DISABLED'
  parentIds: number[]
}>({
  roleName: '',
  description: '',
  status: 'ACTIVE',
  parentIds: [],
})

const parentCandidates = ref<Role[]>([])
const peerCandidates = ref<Role[]>([])

watch(
  () => props.roleId,
  () => void load(),
  { immediate: true },
)

async function load(): Promise<void> {
  if (props.roleId === null) {
    return
  }
  loading.value = true
  try {
    const [roleList, detail, excludedIds] = await Promise.all([
      listRoles(),
      getRole(props.roleId),
      listRoleExclusions(props.roleId),
    ])
    roles.value = roleList
    exclusions.value = excludedIds
    parentCandidates.value = roleList.filter((role) => role.id !== props.roleId)
    peerCandidates.value = roleList.filter(
      (role) => role.id !== props.roleId && !excludedIds.includes(role.id),
    )
    form.roleName = detail.roleName
    form.description = detail.description ?? ''
    form.status = detail.status
    form.parentIds = detail.parentIds
      ?? (detail.parentId !== undefined ? [detail.parentId] : [])
    peerRoleId.value = undefined
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (props.roleId === null || !form.roleName.trim()) {
    ElMessage.warning('角色名称不能为空')
    return
  }
  submitting.value = true
  try {
    await updateRole(props.roleId, {
      roleName: form.roleName.trim(),
      description: form.description.trim(),
      status: form.status,
      parentIds: form.parentIds,
    })
    ElMessage.success('角色继承与状态已更新')
    emit('updated')
    await load()
  } finally {
    submitting.value = false
  }
}

async function addExclusion(): Promise<void> {
  if (props.roleId === null || peerRoleId.value === undefined) {
    ElMessage.warning('请选择互斥角色')
    return
  }
  await bindRoleExclusion(props.roleId, peerRoleId.value)
  ElMessage.success('角色互斥关系已建立')
  emit('updated')
  await load()
}

async function removeExclusion(peerId: number): Promise<void> {
  if (props.roleId === null) {
    return
  }
  await unbindRoleExclusion(props.roleId, peerId)
  ElMessage.success('角色互斥关系已解除')
  emit('updated')
  await load()
}

function roleLabel(roleId: number): string {
  const role = roles.value.find((item) => item.id === roleId)
  return role ? role.roleName : `#${roleId}`
}
</script>

<template>
  <el-card v-loading="loading" shadow="never">
    <template #header>
      <div>
        <h2 class="governance-card__title">继承、启停与互斥</h2>
        <p class="governance-card__hint">多个父角色的权限自动叠加；互斥角色不能同时授予同一用户</p>
      </div>
    </template>
    <el-empty v-if="roleId === null" description="请选择角色" />
    <template v-else>
      <el-form label-position="top">
        <el-form-item label="角色名称">
          <el-input v-model="form.roleName" maxlength="128" />
        </el-form-item>
        <el-form-item label="父角色（可多选）">
          <el-select
            v-model="form.parentIds"
            multiple
            clearable
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="可选一个或多个父角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in parentCandidates"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">有效</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-button
          v-perm="FP.ROLE_UPDATE"
          type="primary"
          :loading="submitting"
          @click="save"
        >
          保存角色配置
        </el-button>
      </el-form>

      <el-divider />
      <div class="governance-card__exclusion-form">
        <el-select
          v-model="peerRoleId"
          filterable
          placeholder="选择互斥角色"
          class="governance-card__peer-select"
        >
          <el-option
            v-for="role in peerCandidates"
            :key="role.id"
            :label="`${role.roleCode} · ${role.roleName}`"
            :value="role.id"
          />
        </el-select>
        <el-button v-perm="FP.ROLE_EXCLUSION_MANAGE" @click="addExclusion">建立互斥</el-button>
      </div>
      <div class="governance-card__tags">
        <template v-if="exclusions.length > 0">
          <el-tag
            v-for="peerId in exclusions"
            :key="peerId"
            closable
            type="warning"
            effect="light"
            @close="removeExclusion(peerId)"
          >
            {{ roleLabel(peerId) }}
          </el-tag>
        </template>
        <span v-else class="governance-card__empty">暂无互斥角色</span>
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.governance-card__title {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.governance-card__hint {
  margin: var(--space-2) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.governance-card__exclusion-form {
  display: flex;
  gap: var(--space-2);
}

.governance-card__peer-select {
  flex: 1;
}

.governance-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.governance-card__empty {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>
