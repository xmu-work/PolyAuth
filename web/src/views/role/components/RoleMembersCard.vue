<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { listRoleAssignments } from '@/api/role.api'
import { getUser } from '@/api/user.api'
import { GrantStatus, GrantStatusLabel, type GrantStatusCode } from '@/constants/enums'
import type { UserRole } from '@/types/role.types'
import type { User } from '@/types/user.types'
import { formatDateTime } from '@/utils/time'

const props = defineProps<{
  roleId: number | null
  reloadSignal?: number
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const assignments = ref<UserRole[]>([])
const userMap = ref<Record<number, User>>({})

const members = computed(() =>
  assignments.value.map((assignment) => ({
    ...assignment,
    account: userMap.value[assignment.userId],
  })),
)

watch(
  () => [props.roleId, props.reloadSignal] as const,
  () => void load(),
  { immediate: true },
)

async function load(): Promise<void> {
  if (props.roleId === null) {
    assignments.value = []
    userMap.value = {}
    error.value = null
    return
  }
  const roleId = props.roleId
  loading.value = true
  error.value = null
  try {
    const list = await listRoleAssignments(roleId)
    const users = await Promise.all(
      list.map((item) => getUser(item.userId).catch(() => null)),
    )
    if (props.roleId !== roleId) {
      return
    }
    assignments.value = list
    userMap.value = Object.fromEntries(
      users
        .filter((user): user is User => user !== null)
        .map((user) => [user.id, user]),
    )
  } catch (caught) {
    if (props.roleId !== roleId) {
      return
    }
    assignments.value = []
    userMap.value = {}
    error.value = caught instanceof Error ? caught.message : '角色成员加载失败'
  } finally {
    if (props.roleId === roleId) {
      loading.value = false
    }
  }
}

function statusLabel(status: GrantStatusCode): string {
  return GrantStatusLabel[status] ?? status
}

function statusType(status: GrantStatusCode): 'success' | 'warning' | 'info' {
  if (status === GrantStatus.ACTIVE) return 'success'
  if (status === GrantStatus.EXPIRED) return 'warning'
  return 'info'
}
</script>

<template>
  <el-card v-loading="loading" class="role-members-card" shadow="never">
    <template #header>
      <div>
        <h2 class="role-members-card__title">角色成员</h2>
        <p class="role-members-card__hint">该角色已授予的用户与授权有效期</p>
      </div>
    </template>

    <el-result v-if="error" icon="error" title="角色成员加载失败" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>
    <el-empty v-else-if="roleId === null" description="请选择角色" :image-size="80" />
    <el-empty v-else-if="members.length === 0" description="该角色尚未授予任何用户" :image-size="80" />
    <div v-else class="role-members-card__list">
      <div v-for="member in members" :key="member.id" class="role-members-card__member">
        <div>
          <strong>{{ member.account?.realName ?? `用户 #${member.userId}` }}</strong>
          <span>{{ member.account?.userNo ?? `#${member.userId}` }}</span>
        </div>
        <el-tag :type="statusType(member.status)" size="small" effect="light">
          {{ statusLabel(member.status) }}
        </el-tag>
        <span class="role-members-card__time">
          {{ formatDateTime(member.effectiveTime) }} 至 {{ formatDateTime(member.expireTime, '长期') }}
        </span>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.role-members-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.role-members-card__title {
  margin: var(--space-0);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.role-members-card__hint {
  margin: var(--space-2) var(--space-0) var(--space-0);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.role-members-card__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.role-members-card__member {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  border: var(--border-width-base) solid var(--color-border-light);
  border-radius: var(--radius-md);
}

.role-members-card__member strong,
.role-members-card__member span {
  display: block;
}

.role-members-card__member > div span {
  margin-top: var(--space-1);
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.role-members-card__time {
  grid-column: 1 / -1;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}
</style>
