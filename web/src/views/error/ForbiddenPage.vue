<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ResultPage from './ResultPage.vue'
import { refreshAccessRoutes, resetAccessState } from '@/router/access'
import { hasAccessibleBusinessRoute } from '@/router/business-routes'
import { useAppStore } from '@/stores/app.store'
import { useAuthStore } from '@/stores/auth.store'
import { usePermissionStore } from '@/stores/permission.store'

const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()
const refreshing = ref(false)
const loggingOut = ref(false)

const hasAccessibleMenu = computed(() =>
  hasAccessibleBusinessRoute(permissionStore.permissionIds),
)

const description = computed(() =>
  hasAccessibleMenu.value
    ? '当前账号未获授权访问该页面。权限变更后，可重新拉取权限或返回首页。'
    : '当前账号暂无权限管理后台访问权限。请联系管理员授权后重新拉取权限，或退出并切换账号。',
)

async function retryPermissions(): Promise<void> {
  refreshing.value = true
  try {
    await refreshAccessRoutes()
    if (hasAccessibleBusinessRoute(permissionStore.permissionIds)) {
      await router.replace('/')
    } else {
      ElMessage.warning('当前账号仍无可访问的管理后台菜单')
    }
  } finally {
    refreshing.value = false
  }
}

async function logoutAndSwitchAccount(): Promise<void> {
  loggingOut.value = true
  try {
    await authStore.logout()
  } catch {
    authStore.clearSession()
  } finally {
    resetAccessState()
    appStore.resetTabs()
    loggingOut.value = false
    await router.replace('/login')
  }
}
</script>

<template>
  <ResultPage status="403" title="暂无访问权限" :description="description">
    <template v-if="hasAccessibleMenu">
      <el-button type="primary" :loading="refreshing" @click="retryPermissions">
        重新拉取权限
      </el-button>
      <el-button @click="router.replace('/')">返回首页</el-button>
      <el-button :loading="loggingOut" @click="logoutAndSwitchAccount">退出登录</el-button>
    </template>
    <template v-else>
      <el-button type="primary" :loading="loggingOut" @click="logoutAndSwitchAccount">
        {{ authStore.hasValidSession ? '退出登录 / 切换账号' : '返回登录' }}
      </el-button>
      <el-button v-if="authStore.hasValidSession" :loading="refreshing" @click="retryPermissions">
        重新拉取权限
      </el-button>
    </template>
  </ResultPage>
</template>
