<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  bindRoleDataScope,
  createDataScope,
  listDataScopes,
  listRoleDataScopes,
  revokeRoleDataScope,
} from '@/api/dataScope.api'
import { listRoles } from '@/api/role.api'
import { getOrgTree } from '@/api/org.api'
import { useDict } from '@/composables/useDict'
import {
  DataScopeType,
  OrgType,
  type DataScopeTypeCode,
  type OrgTypeCode,
} from '@/constants/enums'
import { ADMIN_ACTION_PERMISSIONS as FP } from '@/constants/permissions'
import type { DataScope, RoleDataScope } from '@/types/dataScope.types'
import type { Org } from '@/types/org.types'
import type { Role } from '@/types/role.types'
import { formatDateTime, toLocalISO } from '@/utils/time'

/** RBAC3 数据权限配置页：范围字典、角色范围绑定和临时授权。 */
const { dictLabel, dataScopeTypeOptions } = useDict()

const dictionaries = ref<DataScope[]>([])
const roles = ref<Role[]>([])
const organizations = ref<Org[]>([])
const bindings = ref<RoleDataScope[]>([])
const selectedRoleId = ref<number | null>(null)
const loading = ref(false)
const submitting = ref(false)

const dictionaryDialogVisible = ref(false)
const dictionaryForm = reactive({
  scopeCode: '',
  scopeName: '',
  description: '',
  sortNo: 0,
})

const bindingForm = reactive<{
  scopeType: DataScopeTypeCode
  targetOrgId?: number
  expireTime: string
}>({
  scopeType: DataScopeType.ALL,
  targetOrgId: undefined,
  expireTime: '',
})

const targetOrgType = computed<OrgTypeCode | null>(() => {
  if (bindingForm.scopeType === DataScopeType.BRANCH) {
    return OrgType.BRANCH
  }
  if (bindingForm.scopeType === DataScopeType.DEPT) {
    return OrgType.DEPT
  }
  if (bindingForm.scopeType === DataScopeType.PROJECT) {
    return OrgType.PROJECT
  }
  return null
})

const targetOrganizations = computed(() =>
  organizations.value.filter((org) => org.orgType === targetOrgType.value),
)

const needsTarget = computed(() => targetOrgType.value !== null)

watch(
  () => bindingForm.scopeType,
  () => {
    bindingForm.targetOrgId = undefined
  },
)

watch(
  selectedRoleId,
  () => void loadBindings(),
)

async function loadBaseData(): Promise<void> {
  loading.value = true
  try {
    const [scopeList, roleList, orgList] = await Promise.all([
      listDataScopes(),
      listRoles(),
      getOrgTree(1),
    ])
    dictionaries.value = scopeList
    roles.value = roleList
    organizations.value = orgList.filter((org) => org.status === 'ACTIVE')
    if (selectedRoleId.value === null && roles.value.length > 0) {
      selectedRoleId.value = roles.value[0]?.id ?? null
    }
  } finally {
    loading.value = false
  }
}

async function loadBindings(): Promise<void> {
  if (selectedRoleId.value === null) {
    bindings.value = []
    return
  }
  bindings.value = await listRoleDataScopes(selectedRoleId.value)
}

function openDictionaryDialog(): void {
  dictionaryForm.scopeCode = ''
  dictionaryForm.scopeName = ''
  dictionaryForm.description = ''
  dictionaryForm.sortNo = dictionaries.value.length
  dictionaryDialogVisible.value = true
}

async function submitDictionary(): Promise<void> {
  const scopeCode = dictionaryForm.scopeCode.trim()
  const scopeName = dictionaryForm.scopeName.trim()
  if (!scopeCode || !scopeName) {
    ElMessage.warning('范围编码和范围名称不能为空')
    return
  }
  submitting.value = true
  try {
    await createDataScope({
      scopeCode,
      scopeName,
      description: dictionaryForm.description.trim() || undefined,
      sortNo: dictionaryForm.sortNo,
    })
    ElMessage.success('数据范围字典已创建')
    dictionaryDialogVisible.value = false
    await loadBaseData()
  } finally {
    submitting.value = false
  }
}

async function submitBinding(): Promise<void> {
  if (selectedRoleId.value === null) {
    ElMessage.warning('请先选择角色')
    return
  }
  if (needsTarget.value && bindingForm.targetOrgId === undefined) {
    ElMessage.warning('请选择数据范围目标组织')
    return
  }
  const payload = {
    scopeType: bindingForm.scopeType,
    orgId:
      targetOrgType.value === OrgType.BRANCH || targetOrgType.value === OrgType.DEPT
        ? bindingForm.targetOrgId
        : undefined,
    projectId:
      targetOrgType.value === OrgType.PROJECT ? bindingForm.targetOrgId : undefined,
    expireTime: bindingForm.expireTime
      ? toLocalISO(bindingForm.expireTime)
      : undefined,
  }
  submitting.value = true
  try {
    await bindRoleDataScope(selectedRoleId.value, payload)
    ElMessage.success('角色数据范围已授权')
    bindingForm.targetOrgId = undefined
    bindingForm.expireTime = ''
    await loadBindings()
  } finally {
    submitting.value = false
  }
}

async function revokeBinding(binding: RoleDataScope): Promise<void> {
  if (selectedRoleId.value === null) {
    return
  }
  await revokeRoleDataScope(selectedRoleId.value, binding.id)
  ElMessage.success('数据范围授权已回收')
  await loadBindings()
}

function bindingTarget(binding: RoleDataScope): string {
  const id = binding.orgId ?? binding.projectId
  if (id === undefined) {
    return '—'
  }
  return organizations.value.find((org) => org.id === id)?.orgName ?? `#${id}`
}

function asBinding(row: unknown): RoleDataScope {
  return row as RoleDataScope
}

onMounted(() => void loadBaseData())
</script>

<template>
  <div class="data-scope-page">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="data-scope-page__header">
          <div>
            <h2 class="data-scope-page__title">数据范围字典</h2>
            <p class="data-scope-page__hint">全公司、分公司、部门、项目与本人范围的基础定义</p>
          </div>
          <el-button
            v-perm="FP.DATA_SCOPE_CREATE"
            type="primary"
            :icon="Plus"
            @click="openDictionaryDialog"
          >
            新建范围
          </el-button>
        </div>
      </template>
      <el-table :data="dictionaries">
        <el-table-column prop="scopeCode" label="范围编码" min-width="120" />
        <el-table-column prop="scopeName" label="范围名称" min-width="130" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="light">
              {{ row.status === 'ACTIVE' ? '有效' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div>
          <h2 class="data-scope-page__title">角色数据范围授权</h2>
          <p class="data-scope-page__hint">功能权限通过后，目标数据仍需命中至少一个有效数据范围</p>
        </div>
      </template>

      <el-form inline class="data-scope-page__binding-form">
        <el-form-item label="角色">
          <el-select v-model="selectedRoleId" filterable class="data-scope-page__role-select">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="`${role.roleCode} · ${role.roleName}`"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="范围类型">
          <el-select v-model="bindingForm.scopeType" class="data-scope-page__type-select">
            <el-option
              v-for="option in dataScopeTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="needsTarget" label="目标组织">
          <el-select
            v-model="bindingForm.targetOrgId"
            filterable
            class="data-scope-page__org-select"
          >
            <el-option
              v-for="org in targetOrganizations"
              :key="org.id"
              :label="`${org.orgName}（${org.orgCode}）`"
              :value="org.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker
            v-model="bindingForm.expireTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示长期"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            v-perm="FP.ROLE_DATA_SCOPE_BIND"
            type="primary"
            :loading="submitting"
            @click="submitBinding"
          >
            授权
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="bindings">
        <el-table-column label="范围类型" min-width="120">
          <template #default="{ row }">
            {{ dictLabel(dataScopeTypeOptions, row.scopeType) }}
          </template>
        </el-table-column>
        <el-table-column label="目标" min-width="180">
          <template #default="{ row }">{{ bindingTarget(asBinding(row)) }}</template>
        </el-table-column>
        <el-table-column label="生效时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.effectiveTime) }}</template>
        </el-table-column>
        <el-table-column label="失效时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.expireTime, '长期') }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="light">
              {{ row.status === 'ACTIVE' ? '生效中' : row.status === 'EXPIRED' ? '已过期' : '已回收' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              v-if="row.status === 'ACTIVE'"
              title="确认回收该数据范围？"
              confirm-button-text="确认"
              cancel-button-text="取消"
              @confirm="revokeBinding(asBinding(row))"
            >
              <template #reference>
                <el-button
                  v-perm.disable="FP.ROLE_DATA_SCOPE_REVOKE"
                  link
                  type="danger"
                >
                  回收
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dictionaryDialogVisible" title="新建数据范围字典" width="480px">
      <el-form label-position="top">
        <el-form-item label="范围编码" required>
          <el-input v-model="dictionaryForm.scopeCode" maxlength="64" placeholder="如 DOC_SCOPE" />
        </el-form-item>
        <el-form-item label="范围名称" required>
          <el-input v-model="dictionaryForm.scopeName" maxlength="128" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="dictionaryForm.description"
            type="textarea"
            maxlength="500"
            :rows="3"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dictionaryForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictionaryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDictionary">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.data-scope-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.data-scope-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.data-scope-page__title {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.data-scope-page__hint {
  margin: var(--space-2) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.data-scope-page__binding-form {
  margin-bottom: var(--space-3);
}

.data-scope-page__role-select {
  width: 230px;
}

.data-scope-page__type-select {
  width: 150px;
}

.data-scope-page__org-select {
  width: 240px;
}
</style>
