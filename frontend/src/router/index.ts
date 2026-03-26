import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import FileUploadView from '../views/FileUploadView.vue'
import FileListView from '../views/FileListView.vue'
import AdminView from '../views/AdminView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: LoginView,
      meta: { public: true, title: '로그인' },
    },
    {
      path: '/',
      redirect: '/upload',
    },
    {
      path: '/upload',
      component: FileUploadView,
      meta: { title: '파일 업로드', roles: ['ROLE_ADMIN', 'ROLE_END_USER'] },
    },
    {
      path: '/files',
      component: FileListView,
      meta: { title: '처리 목록' },
    },
    {
      path: '/admin',
      component: AdminView,
      meta: { title: '관리자 대시보드', roles: ['ROLE_ADMIN'] },
    },
  ],
})

// 네비게이션 가드
router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  // 공개 페이지는 통과
  if (to.meta.public) return true

  // 미인증 → 로그인 페이지
  if (!authStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 사용자 정보가 없으면 로드 (토큰은 있지만 새로고침 등으로 user가 null인 경우)
  if (!authStore.user) {
    try {
      await authStore.fetchMe()
    } catch {
      authStore.clearTokens()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // 역할 제한 확인
  const allowedRoles = to.meta.roles as string[] | undefined
  if (allowedRoles && authStore.user && !allowedRoles.includes(authStore.user.role)) {
    return { path: '/files' } // 권한 없으면 목록으로
  }

  return true
})

export default router
