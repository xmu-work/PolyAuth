<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePassword } from '@/api/auth.api'

const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ success: [] }>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function validateConfirm(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
): void {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/,
      message: '新密码需为 8-64 位并同时包含字母和数字',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

watch(visible, (open) => {
  if (open) {
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    formRef.value?.clearValidate()
  }
})

async function submit(): Promise<void> {
  if (form.confirmPassword !== form.newPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  const valid = await formRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="修改密码" width="480px" destroy-on-close>
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="修改成功后当前账号的全部登录令牌将失效，需要重新登录。"
    />
    <el-form
      ref="formRef"
      class="change-password-dialog__form"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="submit"
    >
      <el-form-item label="原密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          data-testid="old-password"
          type="password"
          autocomplete="current-password"
          show-password
        />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          data-testid="new-password"
          type="password"
          autocomplete="new-password"
          show-password
        />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          data-testid="confirm-password"
          type="password"
          autocomplete="new-password"
          show-password
          @keyup.enter="submit"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button
        data-testid="submit-password"
        type="primary"
        :loading="submitting"
        @click="submit"
      >
        确认修改
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.change-password-dialog__form {
  margin-top: var(--space-4);
}
</style>
