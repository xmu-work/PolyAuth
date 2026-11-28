<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import ResultPage from './ResultPage.vue'
import { isAccessReady, resetAccessState } from '@/router/access'
import { hasAccessibleBusinessRoute } from '@/router/business-routes'
import { useAppStore } from '@/stores/app.store'
import { useAuthStore } from '@/stores/auth.store'
import { usePermissionStore } from '@/stores/permission.store'

const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()
const returningLogin = ref(false)

const canReturnHome = computed(
  () =>
    authStore.hasValidSession &&
    (!isAccessReady() || hasAccessibleBusinessRoute(permissionStore.permissionIds)),
)

async function handleBack(): Promise<void> {
  if (canReturnHome.value) {
    await router.replace('/')
    return
  }

  returningLogin.value = true
  try {
    await authStore.logout()
  } catch {
    authStore.clearSession()
  } finally {
    resetAccessState()
    appStore.resetTabs()
    returningLogin.value = false
    await router.replace('/login')
  }
}
</script>

<template>
  <ResultPage
    status="404"
    title="页面不存在"
    description="访问地址无效，或对应页面尚未在管理后台中开放。"
  >
    <el-button type="primary" :loading="returningLogin" @click="handleBack">
      {{ canReturnHome ? '返回首页' : '返回登录' }}
    </el-button>
  </ResultPage>
</template>
