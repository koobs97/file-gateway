/**
 * Vue Router 라우터 설정 모듈
 * - 애플리케이션 라우트 정의 (로그인·파일 업로드·파일 목록·관리자·비밀번호 변경)
 * - 네비게이션 가드를 통한 인증·권한·최초 비밀번호 변경 강제 처리
 *   - 미인증 사용자: /login 리다이렉트
 *   - passwordChanged === false: /change-password 강제 이동
 *   - 역할 제한 위반: /files 리다이렉트
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import FileUploadView from '../views/FileUploadView.vue'
import FileListView from '../views/FileListView.vue'
import AdminView from '../views/AdminView.vue'
import LoginView from '../views/LoginView.vue'
import ChangePasswordView from '../views/ChangePasswordView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: LoginView,
      meta: { public: true, title: '로그인' },
    },
    {
      path: '/change-password',
      component: ChangePasswordView,
      meta: { title: '비밀번호 변경' },
    },
    {
      path: '/',
      redirect: '/upload',
    },
    {
      /** 파일 업로드 페이지 (ROLE_ADMIN, ROLE_END_USER 접근 가능) */
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
      /** 관리자 대시보드 (ROLE_ADMIN 전용) */
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

  // 최초 로그인 비밀번호 변경 강제 (passwordChanged === false 명시 비교: undefined/null 제외)
  if (authStore.user && authStore.user.passwordChanged === false && to.path !== '/change-password') {
    return { path: '/change-password' }
  }

  // 역할 제한 확인
  const allowedRoles = to.meta.roles as string[] | undefined
  if (allowedRoles && authStore.user && !allowedRoles.includes(authStore.user.role)) {
    return { path: '/files' } // 권한 없으면 목록으로
  }

  return true
})

export default router
