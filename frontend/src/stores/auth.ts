import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api/auth'
import type { UserInfo } from '../types/auth'

const TOKEN_KEY = 'fg_access_token'
const REFRESH_KEY = 'fg_refresh_token'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_KEY))
  const user = ref<UserInfo | null>(null)

  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.role === 'ROLE_ADMIN')
  const isAuditor = computed(() => user.value?.role === 'ROLE_AUDITOR')
  const canUpload = computed(() =>
    ['ROLE_ADMIN', 'ROLE_END_USER'].includes(user.value?.role ?? '')
  )
  const canViewAudit = computed(() =>
    ['ROLE_ADMIN', 'ROLE_AUDITOR'].includes(user.value?.role ?? '')
  )

  function setTokens(access: string, refresh: string) {
    accessToken.value = access
    refreshToken.value = refresh
    localStorage.setItem(TOKEN_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  }

  function clearTokens() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  }

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    const { accessToken: at, refreshToken: rt } = res.data.data
    setTokens(at, rt)
    await fetchMe()
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clearTokens()
    }
  }

  async function fetchMe() {
    const res = await authApi.me()
    user.value = res.data.data
  }

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
