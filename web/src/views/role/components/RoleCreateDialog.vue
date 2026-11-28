<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { createRole, listRoles } from '@/api/role.api'
import { RoleType } from '@/constants/enums'
import { toFieldErrors } from '@/utils/formErrors'
import type { Role } from '@/types/role.types'

/** 新建角色（契约 8.5）：角色编码由后端自动生成，界面不展示。 */
const emit = defineEmits<{ success: [role: Role] }>()

const visible = defineModel<boolean>({ default: false })

const formRef = ref<FormInstance>()
const submitting = ref(false)
const serverErrors = ref<Record<string, string>>({})

interface RoleCreateForm {
  roleName: string
  description: string
  roleType: (typeof RoleType)[keyof typeof RoleType]
  parentIds: number[]
}

const form = reactive<RoleCreateForm>({
  roleName: '',
  description: '',
  roleType: RoleType.FUNC,
  parentIds: [],
})

const roles = ref<Role[]>([])

watch(visible, (open) => {
  if (!open) {
    return
  }
  form.roleName = ''
  form.description = ''
  form.roleType = RoleType.FUNC
  form.parentIds = []
  serverErrors.value = {}
  formRef.value?.clearValidate()
  void listRoles().then((items) => {
    roles.value = items
  })
})

watch(form, () => {
  if (Object.keys(serverErrors.value).length > 0) {
    serverErrors.value = {}
  }
})

const rules: FormRules = {
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 50, message: '角色名称不超过 50 个字符', trigger: 'blur' },
  ],
  description: [{ max: 200, message: '描述不超过 200 个字符', trigger: 'blur' }],
}

async function submit(): Promise<void> {
  const valid = await formRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  serverErrors.value = {}
  try {
    const role = await createRole({
      roleName: form.roleName.trim(),
      roleType: form.roleType,
      description: form.description.trim() || undefined,
      parentIds: form.parentIds.length > 0 ? form.parentIds : undefined,
    })
    ElMessage.success(`角色「${role.roleName}」创建成功`)
    visible.value = false
    emit('success', role)
  } catch (error) {
    serverErrors.value = toFieldErrors(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="新建角色" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="角色名称" prop="roleName" :error="serverErrors.roleName">
        <el-input v-model="form.roleName" maxlength="50" placeholder="如 合同审计员" />
      </el-form-item>
      <el-form-item label="父角色（继承，可多选）">
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
            v-for="role in roles"
            :key="role.id"
            :label="role.roleName"
            :value="role.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="描述（选填）" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="角色的职责说明"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>
