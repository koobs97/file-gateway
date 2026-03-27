<!--
  App
  - 애플리케이션 최상위 루트 컴포넌트
  - 전역 헤더(네비게이션, 사용자 정보, 세션 타이머, 테마 토글)를 렌더링한다
  - 로그인/비밀번호 변경 페이지에서는 헤더를 숨긴다
  - 로그아웃 확인 다이얼로그를 포함하며 로그아웃 완료 시 /login 으로 이동한다
-->
<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { computed, ref } from 'vue'
import ThemeToggle from './components/ThemeToggle.vue'
import SessionTimer from './components/SessionTimer.vue'
import { SwitchButton, Loading } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

/**
 * 현재 사용자의 역할 코드를 한국어 레이블로 변환한다.
 *
 * @returns 역할 한국어 이름 (예: '관리자', '감사자')
 */
const roleLabel = computed(() => {
  const map: Record<string, string> = {
    ROLE_ADMIN: '관리자',
    ROLE_AUDITOR: '감사자',
    ROLE_END_USER: '일반 사용자',
    ROLE_API_CLIENT: 'API 클라이언트',
  }
  return map[authStore.user?.role ?? ''] ?? authStore.user?.role ?? ''
})

/**
 * 역할에 따라 Element Plus 태그/아바타에 사용할 색상 타입을 반환한다.
 *
 * @returns Element Plus 컬러 타입 문자열 ('danger' | 'warning' | 'success' | 'info')
 */
const roleType = computed(() => {
  const map: Record<string, string> = {
    ROLE_ADMIN: 'danger',
    ROLE_AUDITOR: 'warning',
    ROLE_END_USER: 'success',
    ROLE_API_CLIENT: 'info',
  }
  return map[authStore.user?.role ?? ''] ?? 'info'
})

/** 로그아웃 확인 다이얼로그 표시 여부 */
const logoutDialogVisible = ref(false)

/** 로그아웃 API 호출 중 로딩 상태 */
const loggingOut = ref(false)

/**
 * 로그아웃을 수행하고 /login 으로 리다이렉트한다.
 * authStore.logout() 호출 후 다이얼로그를 닫는다.
 */
async function confirmLogout() {
  loggingOut.value = true
  await authStore.logout()
  loggingOut.value = false
  logoutDialogVisible.value = false
  router.push('/login')
}

/** 현재 경로가 헤더를 숨겨야 하는 페이지인지 여부 */
const isLoginPage = computed(() => route.path === '/login' || route.path === '/change-password')
</script>

<template>
  <el-container class="layout">
    <!-- 로그인 페이지에서는 헤더 숨김 -->
    <el-header v-if="!isLoginPage" class="header">
      <div class="header-left">
        <span class="logo">File Gateway <span class="logo-accent">CDR</span></span>
      </div>

      <el-menu mode="horizontal" :default-active="route.path" router class="nav-menu">
        <el-menu-item v-if="authStore.canUpload" index="/upload">파일 업로드</el-menu-item>
        <el-menu-item index="/files">처리 목록</el-menu-item>
        <el-menu-item v-if="authStore.isAdmin" index="/admin">시스템 관리</el-menu-item>
      </el-menu>

      <div class="header-right">
        <SessionTimer />
        <ThemeToggle />
        <template v-if="authStore.isAuthenticated">
          <div class="user-panel">
            <div class="user-avatar" :class="`avatar-${roleType}`">
              {{ authStore.user?.username?.charAt(0).toUpperCase() }}
            </div>
            <div class="user-meta">
              <span class="username">{{ authStore.user?.username }}</span>
              <el-tag :type="(roleType as any)" size="small" effect="light" class="role-tag">
                {{ roleLabel }}
              </el-tag>
            </div>
            <div class="panel-sep"></div>
            <button type="button" class="logout-icon-btn" title="로그아웃" @click="logoutDialogVisible = true">
              <el-icon><SwitchButton /></el-icon>
            </button>
          </div>
        </template>
      </div>
    </el-header>

    <el-main :class="{ 'login-main': isLoginPage }">
      <router-view />
    </el-main>
  </el-container>

  <!-- 로그아웃 확인 다이얼로그 -->
  <el-dialog
    v-model="logoutDialogVisible"
    :show-close="false"
    width="360px"
    align-center
    class="logout-dialog"
  >
    <div class="logout-dialog-body">
      <div class="logout-icon-wrap">
        <el-icon class="logout-big-icon"><SwitchButton /></el-icon>
      </div>
      <p class="logout-title">로그아웃</p>
      <p class="logout-desc">현재 세션이 종료됩니다.<br>정말 로그아웃 하시겠습니까?</p>
      <div class="logout-user-badge">
        <div class="logout-avatar" :class="`avatar-${roleType}`">
          {{ authStore.user?.username?.charAt(0).toUpperCase() }}
        </div>
        <span class="logout-username">{{ authStore.user?.username }}</span>
        <el-tag :type="(roleType as any)" size="small" effect="light">{{ roleLabel }}</el-tag>
      </div>
    </div>
    <template #footer>
      <div class="logout-dialog-footer">
        <button class="logout-cancel-btn" :disabled="loggingOut" @click="logoutDialogVisible = false">
          취소
        </button>
        <button class="logout-confirm-btn" :class="{ 'is-loading': loggingOut }" :disabled="loggingOut" @click="confirmLogout">
          <el-icon v-if="loggingOut" class="is-loading"><Loading /></el-icon>
          <el-icon v-else><SwitchButton /></el-icon>
          {{ loggingOut ? '로그아웃 중...' : '로그아웃' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  background-color: var(--el-bg-color); /* style.css의 배경색 사용 */
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: var(--el-bg-color-overlay);
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 0 40px;
  height: 54px !important;
  position: sticky;
  top: 0;
  z-index: 1000;
  backdrop-filter: blur(8px); /* 현대적인 글래스모피즘 */
  -webkit-backdrop-filter: blur(8px);
}

.header-left {
  display: flex;
  align-items: center;
}

.logo {
  font-family: 'Outfit', sans-serif;
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  letter-spacing: -0.5px;
  cursor: default;
}

.logo-accent {
  color: var(--el-color-primary);
  font-weight: 800;
  position: relative;
}

.nav-menu {
  flex: 1;
  margin-left: 40px;
  border-bottom: none !important;
  background-color: transparent !important;
  height: 100%;
}

:deep(.el-menu-item) {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular) !important;
  transition: all 0.2s ease;
  padding: 0 16px !important;
  border-bottom: 2px solid transparent !important;
}

:deep(.el-menu-item.is-active) {
  color: var(--el-color-primary) !important;
  font-weight: 700;
  border-bottom-color: var(--el-color-primary) !important;
  border-bottom-width: 2px !important;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 유저 패널 — 묶음 컨테이너 */
.user-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--bg-muted);
  border: 1px solid var(--bg-border);
  border-radius: 10px;
  padding: 5px 8px 5px 6px;
  transition: background 0.2s, border-color 0.2s;
}

/* 아바타 (이니셜 원형) */
.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  letter-spacing: 0;
}
.avatar-danger  { background: var(--color-danger); }
.avatar-warning { background: var(--color-warning); }
.avatar-success { background: var(--color-success); }
.avatar-info    { background: var(--color-info); }

/* 이름 + 역할 묶음 */
.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1;
}

.username {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}

.role-tag {
  font-size: 10px !important;
  height: 16px !important;
  line-height: 16px !important;
  padding: 0 5px !important;
  border-radius: 3px !important;
}

/* 패널 내부 구분선 */
.panel-sep {
  width: 1px;
  height: 20px;
  background: var(--bg-border);
  flex-shrink: 0;
}

/* 로그아웃 아이콘 버튼 */
.logout-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 15px;
  transition: background 0.2s, color 0.2s;
  flex-shrink: 0;
}
.logout-icon-btn:hover {
  background: var(--color-danger);
  color: #fff;
}

.el-main {
  padding: 24px 20px !important;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.login-main {
  max-width: 100% !important;
  padding: 0 !important;
}
</style>

<style>
/* 로그아웃 다이얼로그 — scoped 밖 (el-dialog 오버레이 대상) */
.logout-dialog .el-dialog {
  border-radius: 16px !important;
  padding: 0 !important;
  overflow: hidden;
  border: 1px solid var(--bg-border);
  background: var(--bg-surface) !important;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.18) !important;
}

.logout-dialog .el-dialog__header {
  display: none !important;
}

.logout-dialog .el-dialog__body {
  padding: 32px 28px 20px !important;
}

.logout-dialog .el-dialog__footer {
  padding: 0 28px 28px !important;
}

/* 다이얼로그 내부 콘텐츠 */
.logout-dialog-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.logout-icon-wrap {
  width: 60px;
  height: 60px;
  border-radius: 16px;
  background: rgba(208, 48, 80, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}

.logout-big-icon {
  font-size: 28px;
  color: var(--color-danger);
}

.logout-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.3px;
}

.logout-desc {
  font-size: 13.5px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.6;
}

.logout-user-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 6px;
  background: var(--bg-muted);
  border: 1px solid var(--bg-border);
  border-radius: 10px;
  padding: 6px 12px;
}

.logout-avatar {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}
.logout-avatar.avatar-danger  { background: var(--color-danger); }
.logout-avatar.avatar-warning { background: var(--color-warning); }
.logout-avatar.avatar-success { background: var(--color-success); }
.logout-avatar.avatar-info    { background: var(--color-info); }

.logout-username {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 푸터 버튼 */
.logout-dialog-footer {
  display: flex;
  gap: 10px;
}

.logout-cancel-btn,
.logout-confirm-btn {
  flex: 1;
  height: 40px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: background 0.18s, border-color 0.18s, opacity 0.18s;
  border: 1px solid;
}

.logout-cancel-btn {
  background: var(--bg-muted);
  border-color: var(--bg-border);
  color: var(--text-secondary);
}
.logout-cancel-btn:hover:not(:disabled) {
  background: var(--bg-surface);
  border-color: var(--text-muted);
  color: var(--text-primary);
}

.logout-confirm-btn {
  background: var(--color-danger);
  border-color: var(--color-danger);
  color: #fff;
}
.logout-confirm-btn:hover:not(:disabled) {
  background: #b82040;
  border-color: #b82040;
}

.logout-cancel-btn:disabled,
.logout-confirm-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
