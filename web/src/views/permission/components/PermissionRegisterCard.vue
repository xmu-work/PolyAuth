<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { createPermission } from '@/api/system.api'
import {
  OperationAction,
  OperationActionLabel,
  type OperationActionCode,
} from '@/constants/enums'
import { ADMIN_ACTION_PERMISSIONS as FP } from '@/constants/permissions'
import { buildPermissionTree } from '@/utils/permissionTree'
import { toFieldErrors } from '@/utils/formErrors'
import type { Permission, System } from '@/types/system.types'

const props = defineProps<{
  systems: System[]
  permissions: Permission[]
  selectedSystemId?: number | null
  embedded?: boolean
}>()
const emit = defineEmits<{ created: [] }>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const serverErrors = ref<Record<string, string>>({})
const permissionTree = computed(() => buildPermissionTree(props.permissions))
const actionOptions = Object.values(OperationAction).map((value) => ({
  value,
  label: OperationActionLabel[value],
}))
const form = reactive({
  systemId: undefined as number | undefined,
  parentId: undefined as number | undefined,
  permissionCode: '',
  permissionName: '',
  permissionType: 'MENU' as 'MENU' | 'BUTTON',
  action: OperationAction.VIEW as OperationActionCode,
  routePath: '',
  sortNo: undefined as number | undefined,
})

watch(
  () => props.selectedSystemId,
  (systemId) => {
    if (systemId !== null && systemId !== undefined) {
      form.systemId = systemId
    }
  },
  { immediate: true },
)

const rules: FormRules = {
  systemId: [{ required: true, message: '请选择所属系统', trigger: 'change' }],
  permissionCode: [
    { required: true, message: '请输入权限编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_.-]{1,63}$/, message: '编码以大写字母开头，2-64 位大写字母/数字/下划线/点/中划线', trigger: 'blur' },
  ],
  permissionName: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  submitting.value = true
  serverErrors.value = {}
  try {
    await createPermission({
      systemId: form.systemId as number,
      parentId: form.parentId,
      permissionCode: form.permissionCode.trim(),
      permissionName: form.permissionName.trim(),
      permissionType: form.permissionType,
      routePath: form.routePath.trim() || undefined,
      action: form.action,
      sortNo: form.sortNo,
    })
    ElMessage.success(`权限「${form.permissionName.trim()}」登记成功`)
    form.permissionCode = ''
    form.permissionName = ''
    form.routePath = ''
    formRef.value?.clearValidate()
    emit('created')
  } catch (error) {
    serverErrors.value = toFieldErrors(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card
    class="permission-register-card"
    :class="{ 'permission-register-card--embedded': embedded }"
    shadow="never"
  >
    <template v-if="!embedded" #header>
      <div>
        <h2 class="permission-register-card__title">菜单 / 按钮权限</h2>
        <p class="permission-register-card__hint">菜单与按钮构成前端权限树；可授权粒度是权限而不是 HTTP 路径</p>
      </div>
    </template>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="所属系统" prop="systemId">
        <el-select
          v-model="form.systemId"
          data-testid="system-select"
          :disabled="selectedSystemId !== null && selectedSystemId !== undefined"
          placeholder="请选择系统"
          style="width: 100%"
        >
          <el-option
            v-for="system in props.systems"
            :key="system.id"
            :label="`${system.systemName}（${system.systemCode}）`"
            :value="system.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="权限类型">
        <el-radio-group v-model="form.permissionType">
          <el-radio value="MENU">菜单</el-radio>
          <el-radio value="BUTTON">按钮</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="父权限">
        <el-tree-select
          v-model="form.parentId"
          :data="permissionTree"
          node-key="id"
          check-strictly
          clearable
          filterable
          :props="{ label: 'permissionName', children: 'children' }"
          placeholder="不选择表示顶级菜单"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="操作码">
        <el-select v-model="form.action" data-testid="action-select" style="width: 100%">
          <el-option
            v-for="option in actionOptions"
            :key="option.value"
            :label="`${option.label}（${option.value}）`"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="权限编码" prop="permissionCode" :error="serverErrors.permissionCode">
        <el-input
          v-model="form.permissionCode"
          data-testid="permission-code"
          maxlength="64"
          placeholder="如 BTN-CONTRACT-EXPORT"
        />
      </el-form-item>
      <el-form-item label="权限名称" prop="permissionName" :error="serverErrors.permissionName">
        <el-input
          v-model="form.permissionName"
          data-testid="permission-name"
          maxlength="50"
          placeholder="如 合同导出"
        />
      </el-form-item>
      <el-form-item v-if="form.permissionType === 'MENU'" label="菜单路由">
        <el-input
          v-model="form.routePath"
          data-testid="route-path"
          maxlength="200"
          placeholder="如 /contract"
        />
      </el-form-item>
      <el-form-item label="排序号（选填）">
        <el-input-number v-model="form.sortNo" :min="0" :max="9999" :precision="0" style="width: 100%" />
      </el-form-item>
      <el-button
        v-perm="FP.PERMISSION_CREATE"
        data-testid="create-permission"
        type="primary"
        :loading="submitting"
        style="width: 100%"
        @click="submit"
      >
        登记权限
      </el-button>
    </el-form>
  </el-card>
</template>

<style scoped>
.permission-register-card { border-radius: var(--radius-lg); box-shadow: var(--shadow-card); }
.permission-register-card--embedded { border: none; box-shadow: none; background: transparent; }
.permission-register-card--embedded :deep(.el-card__body) { padding: var(--space-0); }
.permission-register-card__title { margin: var(--space-0); color: var(--color-text-primary); font-size: var(--font-size-lg); }
.permission-register-card__hint { margin: var(--space-2) var(--space-0) var(--space-0); color: var(--color-text-secondary); font-size: var(--font-size-sm); }
</style>
