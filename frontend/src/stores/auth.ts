/**
 * 인증 Pinia 스토어
 * - JWT 액세스 토큰·리프레시 토큰의 상태 관리 및 localStorage 동기화
 * - 로그인·로그아웃·토큰 재발급·사용자 정보 조회 액션 제공
 * - 역할 기반 권한 computed 속성 제공 (isAdmin, isAuditor, canUpload, canViewAudit)
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api/auth'
import type { UserInfo } from '../types/auth'

/** localStorage 액세스 토큰 키 */
const TOKEN_KEY = 'fg_access_token'
/** localStorage 리프레시 토큰 키 */
const REFRESH_KEY = 'fg_refresh_token'

export const useAuthStore = defineStore('auth', () => {
  // ── 상태 정의 ─────────────────────────────────
  /** 현재 액세스 토큰 (null이면 미인증) */
  const accessToken = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  /** 현재 리프레시 토큰 */
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_KEY))
  /** 로그인한 사용자 정보 */
  const user = ref<UserInfo | null>(null)

  // ── 권한 Computed ──────────────────────────────
  /** 인증 여부 (액세스 토큰 존재 시 true) */
  const isAuthenticated = computed(() => !!accessToken.value)
  /** ROLE_ADMIN 여부 */
  const isAdmin = computed(() => user.value?.role === 'ROLE_ADMIN')
  /** ROLE_AUDITOR 여부 */
  const isAuditor = computed(() => user.value?.role === 'ROLE_AUDITOR')
  /** 파일 업로드 권한 여부 (ROLE_ADMIN 또는 ROLE_END_USER) */
  const canUpload = computed(() =>
    ['ROLE_ADMIN', 'ROLE_END_USER'].includes(user.value?.role ?? '')
  )
  /** 감사 로그 열람 권한 여부 (ROLE_ADMIN 또는 ROLE_AUDITOR) */
  const canViewAudit = computed(() =>
    ['ROLE_ADMIN', 'ROLE_AUDITOR'].includes(user.value?.role ?? '')
  )

  // ── 내부 헬퍼 ─────────────────────────────────
  /**
   * 액세스·리프레시 토큰을 상태와 localStorage에 저장
   *
   * @param access 새 액세스 토큰
   * @param refresh 새 리프레시 토큰
   */
  function setTokens(access: string, refresh: string) {
    accessToken.value = access
    refreshToken.value = refresh
    localStorage.setItem(TOKEN_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  }

  /**
   * 모든 토큰과 사용자 정보를 초기화하고 localStorage에서 제거
   */
  function clearTokens() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  }

  // ── API 액션 ───────────────────────────────────
  /**
   * 로그인: 토큰 저장 후 사용자 정보를 즉시 로드
   *
   * @param username 로그인 아이디
   * @param password 비밀번호
   */
  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    const { accessToken: at, refreshToken: rt } = res.data.data
    setTokens(at, rt)
    await fetchMe()
  }

  /**
   * 로그아웃: 서버 토큰 무효화 후 로컬 상태 초기화
   */
  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clearTokens()
    }
  }

  /**
   * 현재 인증된 사용자 정보를 서버에서 로드하여 상태에 반영
   */
  async function fetchMe() {
    const res = await authApi.me()
    user.value = res.data.data
  }

  /**
   * 리프레시 토큰으로 액세스 토큰 재발급 시도
   *
   * @returns 재발급 성공 여부 (실패 시 토큰 초기화)
   */
  async function tryRefresh(): Promise<boolean> {
    if (!refreshToken.value) return false
    try {
      const res = await authApi.refresh(refreshToken.value)
      const { accessToken: at, refreshToken: rt } = res.data.data
      setTokens(at, rt)
      return true
    } catch {
      clearTokens()
      return false
    }
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    isAdmin,
    isAuditor,
    canUpload,
    canViewAudit,
    login,
    logout,
    fetchMe,
    tryRefresh,
    clearTokens,
  }
})
