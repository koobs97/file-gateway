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
let isRefreshing = false
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

function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

export default client
