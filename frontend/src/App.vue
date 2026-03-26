<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { computed } from 'vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const roleLabel = computed(() => {
  const map: Record<string, string> = {
    ROLE_ADMIN: '관리자',
    ROLE_AUDITOR: '감사자',
    ROLE_END_USER: '일반 사용자',
    ROLE_API_CLIENT: 'API 클라이언트',
  }
  return map[authStore.user?.role ?? ''] ?? authStore.user?.role ?? ''
})

const roleType = computed(() => {
  const map: Record<string, string> = {
    ROLE_ADMIN: 'danger',
    ROLE_AUDITOR: 'warning',
    ROLE_END_USER: 'success',
    ROLE_API_CLIENT: 'info',
  }
  return map[authStore.user?.role ?? ''] ?? 'info'
})

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

const isLoginPage = computed(() => route.path === '/login')
</script>

<template>
  <el-container class="layout">
    <!-- 로그인 페이지에서는 헤더 숨김 -->
    <el-header v-if="!isLoginPage" class="header">
      <span class="logo">File Gateway CDR</span>

      <el-menu mode="horizontal" :default-active="route.path" router class="nav-menu">
        <el-menu-item v-if="authStore.canUpload" index="/upload">파일 업로드</el-menu-item>
        <el-menu-item index="/files">처리 목록</el-menu-item>
        <el-menu-item v-if="authStore.isAdmin" index="/admin">관리자</el-menu-item>
      </el-menu>

      <div v-if="authStore.isAuthenticated" class="user-info">
        <el-tag :type="(roleType as any)" size="small" style="margin-right: 8px;">
          {{ roleLabel }}
        </el-tag>
        <span class="username">{{ authStore.user?.username }}</span>
        <el-button size="small" type="info" plain style="margin-left: 12px;" @click="handleLogout">
          로그아웃
        </el-button>
      </div>
    </el-header>

    <el-main :class="{ 'login-main': isLoginPage }">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
.layout { min-height: 100vh; background: #f5f7fa; }
.header {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 24px;
}
.logo { font-size: 18px; font-weight: 700; color: #409eff; white-space: nowrap; }
.nav-menu { flex: 1; border-bottom: none; }
.user-info { display: flex; align-items: center; white-space: nowrap; }
.username { font-size: 14px; color: #303133; font-weight: 500; }
.el-main { padding: 24px; max-width: 1200px; margin: 0 auto; width: 100%; }
.login-main { max-width: 100% !important; padding: 0 !important; }
</style>
