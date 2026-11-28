<script setup lang="ts">
import { ref, watch } from 'vue'
import { getSensitiveUser, getUser } from '@/api/user.api'
import { useDict } from '@/composables/useDict'
import { useOrgNames } from '@/composables/useOrgTree'
import { useUserAuthorization, type EffectiveRole } from '@/composables/useUserAuthorization'
import {
  AccountStatus,
  OperationActionLabel,
  type OperationActionCode,
} from '@/constants/enums'
import type { User } from '@/types/user.types'
import type { SensitiveUser } from '@/types/user.types'
import { formatDateTime } from '@/utils/time'
import { ADMIN_ACTION_PERMISSIONS as FP } from '@/constants/permissions'

/**
 * 用户详情抽屉（契约 8.4 / 10.3）：只渲染安全视图字段（realName/phoneMasked）。
 * 若后端误回 nameEnc/phoneEnc/*Hash 等密文字段，User 类型未声明、模板不渲染（红线 10.3）。
 * 角色与权限区复用现有授权/权限接口，按后端判定链聚合成生效角色与生效权限。
 */
const props = defineProps<{
  userId: number | null
}>()

const visible = defineModel<boolean>({ default: false })

const { dictLabel, accountStatusOptions } = useDict()
const { resolve: resolveOrgNames, nameOf } = useOrgNames()

const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<User | null>(null)
const sensitive = ref<SensitiveUser | null>(null)
const sensitiveLoading = ref(false)

const {
  roles: authorizationRoles,
  groups: authorizationGroups,
  loading: authorizationLoading,
  error: authorizationError,
  totalPermissions,
  load: loadAuthorization,
  reset: resetAuthorization,
} = useUserAuthorization()

const expandedSystems = ref<number[]>([])

watch(
  authorizationGroups,
  (groups) => {
    expandedSystems.value = groups.map((group) => group.systemId)
  },
  { immediate: true },
)

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
const STATUS_TAG: Record<string, TagType> = {
  [AccountStatus.NORMAL]: 'success',
  [AccountStatus.DISABLED]: 'info',
  [AccountStatus.SEALED]: 'danger',
  [AccountStatus.LOCKED]: 'warning',
}

watch(
  () => [visible.value, props.userId] as const,
  () => void loadDetail(),
  { immediate: true },
)

async function loadDetail(): Promise<void> {
  if (!visible.value || props.userId === null) {
    return
  }
  const userId = props.userId
  loading.value = true
  error.value = null
  resetAuthorization()
  void loadAuthorization(userId)
  try {
    const user = await getUser(userId)
    if (props.userId !== userId) {
      return
    }
    detail.value = user
    sensitive.value = null
    await resolveOrgNames([user.orgId])
  } catch (caught) {
    if (props.userId !== userId) {
      return
    }
    detail.value = null
    error.value = caught instanceof Error ? caught.message : '用户详情加载失败'
  } finally {
    if (props.userId === userId) {
      loading.value = false
    }
  }
}

async function revealSensitive(): Promise<void> {
  if (props.userId === null) {
    return
  }
  sensitiveLoading.value = true
  try {
    sensitive.value = await getSensitiveUser(props.userId)
  } finally {
    sensitiveLoading.value = false
  }
}

function dash(value: string | number | null | undefined): string {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

function actionLabel(action: OperationActionCode): string {
  return OperationActionLabel[action] ?? action
}

function permissionTypeLabel(type: 'MENU' | 'BUTTON'): string {
  return type === 'MENU' ? '菜单' : '按钮'
}

/** 直接授予角色展示有效期；继承角色只说明来源。 */
function grantPeriod(role: EffectiveRole): string {
  if (!role.grant) {
    return '由父角色继承'
  }
  const start = role.grant.effectiveTime ? formatDateTime(role.grant.effectiveTime) : '立即生效'
  const end = role.grant.expireTime ? formatDateTime(role.grant.expireTime) : '长期'
  return `有效期：${start} 至 ${end}`
}
</script>

<template>
  <el-drawer v-model="visible" title="用户详情" size="680px">
    <el-skeleton v-if="loading" :rows="6" animated />
    <el-result v-else-if="error" icon="error" title="用户详情加载失败" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="loadDetail">重试</el-button>
      </template>
    </el-result>
    <template v-else-if="detail">
      <div class="user-detail-drawer__actions">
        <el-button
          v-perm="FP.USER_SENSITIVE_VIEW"
          type="warning"
          plain
          :loading="sensitiveLoading"
          @click="revealSensitive"
        >
          查看姓名与电话明文
        </el-button>
      </div>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户账号">{{ detail.userNo }}</el-descriptions-item>
        <el-descriptions-item label="姓名（脱敏）">{{ detail.realName }}</el-descriptions-item>
        <el-descriptions-item label="手机号（脱敏）">{{ dash(detail.phoneMasked) }}</el-descriptions-item>
        <el-descriptions-item label="所属组织">{{ nameOf(detail.orgId) }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ dash(detail.post) }}</el-descriptions-item>
        <el-descriptions-item label="账号状态">
          <el-tag :type="STATUS_TAG[detail.accountStatus]" effect="light">
            {{ dictLabel(accountStatusOptions, detail.accountStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最近登录">{{ formatDateTime(detail.lastLoginAt) }}</el-descriptions-item>
        <el-descriptions-item label="离职时间">{{ formatDateTime(detail.leaveTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(detail.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-descriptions
        v-if="sensitive"
        class="user-detail-drawer__sensitive"
        :column="1"
        border
      >
        <el-descriptions-item label="姓名明文">{{ sensitive.realName }}</el-descriptions-item>
        <el-descriptions-item label="电话明文">{{ dash(sensitive.phone) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        class="user-detail-drawer__note"
        type="info"
        :closable="false"
        show-icon
        title="安全说明：姓名与手机号均为后端脱敏视图字段；密文/哈希字段（10.3 红线）不展示。"
      />

      <el-divider class="user-detail-drawer__divider" />

      <section class="user-detail-drawer__authorization">
        <div class="user-detail-drawer__section-header">
          <div>
            <h3 class="user-detail-drawer__section-title">角色与权限</h3>
            <p class="user-detail-drawer__section-hint">
              统计当前生效角色（含父角色继承）与角色授予的前端权限；后端 API 权限随所映射的前端权限生效。
            </p>
          </div>
        </div>

        <el-alert
          v-if="detail.accountStatus !== AccountStatus.NORMAL"
          class="user-detail-drawer__note"
          type="warning"
          :closable="false"
          show-icon
          :title="`账号当前为「${dictLabel(accountStatusOptions, detail.accountStatus)}」状态，后端权限判定会直接拒绝；以下展示已绑定的角色与权限。`"
        />

        <el-skeleton v-if="authorizationLoading" :rows="4" animated />
        <el-alert
          v-else-if="authorizationError"
          class="user-detail-drawer__note"
          type="warning"
          :closable="false"
          show-icon
          :title="`角色与权限加载失败：${authorizationError}`"
        />
        <template v-else>
          <h4 class="user-detail-drawer__subtitle">
            生效角色
            <el-tag size="small" type="info" effect="plain">
              {{ authorizationRoles.length }} 个
            </el-tag>
          </h4>
          <div v-if="authorizationRoles.length > 0" class="user-detail-drawer__role-list">
            <el-tooltip
              v-for="role in authorizationRoles"
              :key="role.id"
              :content="grantPeriod(role)"
              placement="top"
            >
              <el-tag :type="role.direct ? 'primary' : 'info'" effect="light">
                {{ role.roleName }}
                <span v-if="!role.direct" class="user-detail-drawer__role-inherited">继承</span>
              </el-tag>
            </el-tooltip>
          </div>
          <el-empty v-else description="该用户暂无生效角色" :image-size="60" />

          <h4 class="user-detail-drawer__subtitle">
            生效权限
            <el-tag size="small" type="info" effect="plain">
              {{ totalPermissions }} 项
            </el-tag>
          </h4>
          <el-collapse v-if="authorizationGroups.length > 0" v-model="expandedSystems">
            <el-collapse-item
              v-for="group in authorizationGroups"
              :key="group.systemId"
              :name="group.systemId"
            >
              <template #title>
                <span class="user-detail-drawer__system-name">{{ group.systemName }}</span>
                <code class="user-detail-drawer__system-code">{{ group.systemCode }}</code>
                <el-tag size="small" effect="plain">{{ group.permissions.length }} 项</el-tag>
              </template>
              <el-table :data="group.permissions" size="small" border>
                <el-table-column label="权限" min-width="180">
                  <template #default="{ row }">
                    <div class="user-detail-drawer__permission-name">{{ row.permissionName }}</div>
                    <code class="user-detail-drawer__permission-code">{{ row.permissionCode }}</code>
                  </template>
                </el-table-column>
                <el-table-column label="类型" width="64">
                  <template #default="{ row }">{{ permissionTypeLabel(row.permissionType) }}</template>
                </el-table-column>
                <el-table-column label="授权动作" width="110">
                  <template #default="{ row }">{{ actionLabel(row.allowedAction) }}</template>
                </el-table-column>
                <el-table-column label="来源角色" min-width="140">
                  <template #default="{ row }">
                    <el-tag
                      v-for="roleName in row.sourceRoles"
                      :key="roleName"
                      size="small"
                      type="info"
                      effect="plain"
                      class="user-detail-drawer__source-role"
                    >
                      {{ roleName }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
          <el-empty v-else description="该用户暂无生效权限" :image-size="60" />
        </template>
      </section>
    </template>
    <el-empty v-else description="暂无数据" />
  </el-drawer>
</template>

<style scoped>
.user-detail-drawer__note {
  margin-top: var(--space-4);
}

.user-detail-drawer__actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--space-3);
}

.user-detail-drawer__sensitive {
  margin-top: var(--space-4);
}

.user-detail-drawer__divider {
  margin: var(--space-5) var(--space-0);
}

.user-detail-drawer__section-title {
  margin: var(--space-0);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.user-detail-drawer__section-hint {
  margin: var(--space-2) var(--space-0) var(--space-0);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.user-detail-drawer__subtitle {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-4) var(--space-0) var(--space-3);
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
}

.user-detail-drawer__role-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.user-detail-drawer__role-code {
  margin-left: var(--space-1);
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.user-detail-drawer__role-inherited {
  margin-left: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.user-detail-drawer__system-name {
  margin-right: var(--space-2);
  color: var(--color-text-primary);
  font-weight: 600;
}

.user-detail-drawer__system-code {
  margin-right: var(--space-2);
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.user-detail-drawer__permission-name {
  color: var(--color-text-primary);
}

.user-detail-drawer__permission-code {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.user-detail-drawer__source-role {
  margin: var(--space-1) var(--space-1) var(--space-1) var(--space-0);
}
</style>
