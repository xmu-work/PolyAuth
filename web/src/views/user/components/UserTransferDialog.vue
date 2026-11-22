<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getOrgTree } from '@/api/org.api'
import { transferUser } from '@/api/user.api'
import type { Org } from '@/types/org.types'
import type { User } from '@/types/user.types'

/** 员工调岗对话框：更新组织与岗位，保留现有授权供管理员复核。 */
const props = defineProps<{ user: User | null; loading?: boolean }>()
const emit = defineEmits<{ success: [user: User] }>()
const visible = defineModel<boolean>({ default: false })

const organizations = ref<Org[]>([])
const submitting = ref(false)
const form = reactive({
  orgId: undefined as number | undefined,
  post: '',
})

watch(visible, (open) => {
  if (!open || !props.user) {
    return
  }
  form.orgId = props.user.orgId
  form.post = props.user.post ?? ''
  void getOrgTree(1).then((items) => {
    organizations.value = items.filter((org) => org.status === 'ACTIVE')
  })
})

async function submit(): Promise<void> {
  if (!props.user || form.orgId === undefined) {
    ElMessage.warning('请选择目标组织')
    return
  }
  submitting.value = true
  try {
    const updated = await transferUser(props.user.id, {
      orgId: form.orgId,
      post: form.post.trim() || undefined,
    })
    ElMessage.success('调岗信息已更新，请复核该用户现有角色')
    visible.value = false
    emit('success', updated)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="员工调岗" width="520px">
    <el-alert
      class="transfer-dialog__alert"
      type="info"
      :closable="false"
      show-icon
      title="调岗不会自动删除已有角色；请在授权管理中复核并按需调整。"
    />
    <el-form label-position="top">
      <el-form-item label="员工">
        <el-input :model-value="user ? `${user.userNo} · ${user.realName}` : ''" disabled />
      </el-form-item>
      <el-form-item label="目标组织" required>
        <el-select v-model="form.orgId" filterable style="width: 100%">
          <el-option
            v-for="org in organizations"
            :key="org.id"
            :label="`${org.orgName}（${org.orgCode}）`"
            :value="org.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="岗位">
        <el-input v-model="form.post" maxlength="64" placeholder="如 预算员" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting || loading"
        @click="submit"
      >
        确认调岗
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.transfer-dialog__alert {
  margin-bottom: var(--space-3);
}
</style>
