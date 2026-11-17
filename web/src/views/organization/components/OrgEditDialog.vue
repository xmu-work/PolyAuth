<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { updateOrg } from '@/api/org.api'
import type { Org } from '@/types/org.types'

/** 组织基础信息与启停状态编辑对话框。 */
const props = defineProps<{ org: Org | null }>()
const emit = defineEmits<{ success: [org: Org] }>()
const visible = defineModel<boolean>({ default: false })

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  orgName: '',
  sortNo: 0,
  leaderUserId: undefined as number | undefined,
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED',
})

const rules: FormRules = {
  orgName: [
    { required: true, message: '请输入组织名称', trigger: 'blur' },
    { max: 128, message: '组织名称不超过 128 个字符', trigger: 'blur' },
  ],
  sortNo: [{ type: 'integer', min: 0, max: 9999, message: '排序号为 0-9999', trigger: 'blur' }],
}

watch(visible, (open) => {
  if (!open || !props.org) {
    return
  }
  form.orgName = props.org.orgName
  form.sortNo = props.org.sortNo
  form.leaderUserId = props.org.leaderUserId
  form.status = props.org.status
  formRef.value?.clearValidate()
})

async function submit(): Promise<void> {
  if (!props.org) {
    return
  }
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    const updated = await updateOrg(props.org.id, {
      orgName: form.orgName.trim(),
      sortNo: form.sortNo,
      leaderUserId: form.leaderUserId,
      status: form.status,
    })
    ElMessage.success('组织信息已更新')
    visible.value = false
    emit('success', updated)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="编辑组织" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="组织名称" prop="orgName">
        <el-input v-model="form.orgName" maxlength="128" />
      </el-form-item>
      <el-form-item label="排序号" prop="sortNo">
        <el-input-number v-model="form.sortNo" :min="0" :max="9999" style="width: 100%" />
      </el-form-item>
      <el-form-item label="负责人用户 ID">
        <el-input-number v-model="form.leaderUserId" :min="1" style="width: 100%" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio value="ACTIVE">启用</el-radio>
          <el-radio value="DISABLED">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-alert
        v-if="form.status === 'DISABLED'"
        type="warning"
        :closable="false"
        show-icon
        title="停用会同时停用全部下级组织；已绑定数据范围将无法匹配这些节点。"
      />
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
