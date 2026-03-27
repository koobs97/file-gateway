/**
 * Axios HTTP 클라이언트 모듈
 * - 공통 baseURL 및 타임아웃 설정
 * - 요청 인터셉터: localStorage에서 액세스 토큰을 읽어 Authorization 헤더에 자동 첨부
 * - 응답 인터셉터: 401 수신 시 리프레시 토큰으로 재발급 후 대기 요청 일괄 재시도
 * - 리프레시 실패 또는 토큰 미존재 시 /login 으로 리다이렉트
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

// 요청 인터셉터: Authorization 헤더 자동 첨부
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('fg_access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 응답 인터셉터: 401 → 토큰 리프레시, 그 외 에러 메시지 표시
/** 현재 리프레시 요청이 진행 중인지 여부 */
let isRefreshing = false
/** 리프레시 완료를 대기 중인 요청 큐 */
let pendingQueue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = []

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // 401이고 /auth/ 경로가 아니면 토큰 리프레시 시도
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/')
    ) {
      if (isRefreshing) {
        // 리프레시 진행 중이면 대기열에 추가
        return new Promise((resolve, reject) => {
          pendingQueue.push({
            resolve: (token) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(client(originalRequest))
            },
            reject,
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = localStorage.getItem('fg_refresh_token')
      if (!refreshToken) {
        isRefreshing = false
        redirectToLogin()
        return Promise.reject(error)
      }

      try {
        const res = await axios.post(
          '/api/v1/auth/refresh',
          null,
          { headers: { 'Refresh-Token': refreshToken } }
        )
        const { accessToken, refreshToken: newRefresh } = res.data.data
        localStorage.setItem('fg_access_token', accessToken)
        localStorage.setItem('fg_refresh_token', newRefresh)

        pendingQueue.forEach((p) => p.resolve(accessToken))
        pendingQueue = []

        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return client(originalRequest)
      } catch {
        pendingQueue.forEach((p) => p.reject(error))
        pendingQueue = []
        localStorage.removeItem('fg_access_token')
        localStorage.removeItem('fg_refresh_token')
        redirectToLogin()
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }

    const message = error.response?.data?.error?.message ?? '요청 처리 중 오류가 발생했습니다.'
    if (error.response?.status !== 401) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

/**
 * 로그인 페이지로 리다이렉트
 *
 * @remarks 이미 /login 경로에 있는 경우 중복 리다이렉트를 방지한다.
 */
function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    ElMessage({
      message: '세션이 만료되었습니다. 로그인 페이지로 이동합니다.',
      type: 'warning',
      duration: 2500,
      showClose: true,
    })
    setTimeout(() => { window.location.href = '/login' }, 1800)
  }
}

export default client
