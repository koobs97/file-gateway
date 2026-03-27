/**
 * 세션 타이머 컴포저블
 * - JWT 액세스 토큰의 만료 시각을 파싱하여 남은 시간을 1초 단위로 계산
 * - 남은 시간 기반의 단계별 경고 상태 제공 (isVisible, isWarning, isDanger, isExpired)
 * - 컴포넌트 마운트 시 타이머 시작, 언마운트 시 자동 해제
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'

/**
 * JWT 토큰 페이로드에서 만료 시각(exp)을 밀리초로 파싱
 *
 * @param token JWT 액세스 토큰 문자열
 * @returns 만료 시각 (Unix 밀리초), 파싱 실패 시 null
 */
function parseJwtExpiry(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp * 1000
  } catch {
    return null
  }
}

/** 경고 표시 기준: 5분 미만 남은 경우 */
const WARN_MS  = 5 * 60 * 1000   // 5분 미만 → 주의
/** 위험 표시 기준: 2분 미만 남은 경우 */
const DANGER_MS = 2 * 60 * 1000  // 2분 미만 → 위험
/** 타이머 UI 노출 기준: 30분 미만 남은 경우 */
const SHOW_MS  = 30 * 60 * 1000  // 30분 미만부터 표시

/**
 * 세션 타이머 컴포저블 훅
 *
 * @returns formatted (남은 시간 문자열), remainingSeconds, isVisible, isWarning, isDanger, isExpired
 */
export function useSessionTimer() {
  /** 현재 남은 세션 시간 (밀리초) */
  const remainingMs = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  /**
   * localStorage에서 토큰을 읽어 남은 시간을 갱신
   */
  function update() {
    const token = localStorage.getItem('fg_access_token')
    if (!token) { remainingMs.value = 0; return }
    const expiry = parseJwtExpiry(token)
    if (!expiry) { remainingMs.value = 0; return }
    remainingMs.value = Math.max(0, expiry - Date.now())
  }

  onMounted(() => { update(); timer = setInterval(update, 1000) })
  onUnmounted(() => { if (timer) clearInterval(timer) })

  /** 남은 시간 (초 단위 정수) */
  const remainingSeconds = computed(() => Math.floor(remainingMs.value / 1000))

  /**
   * 남은 시간을 "HH:MM:SS" 또는 "MM:SS" 형식의 문자열로 반환
   */
  const formatted = computed(() => {
    const s = remainingSeconds.value
    const h = Math.floor(s / 3600)
    const m = Math.floor((s % 3600) / 60)
    const sec = s % 60
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
    return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  })

  /** 타이머 UI 표시 여부 (토큰 존재 & 30분 미만) */
  const isVisible = computed(() => remainingMs.value > 0 && remainingMs.value < SHOW_MS)
  /** 주의 상태 여부 (5분 미만) */
  const isWarning = computed(() => remainingMs.value > 0 && remainingMs.value < WARN_MS)
  /** 위험 상태 여부 (2분 미만) */
  const isDanger  = computed(() => remainingMs.value > 0 && remainingMs.value < DANGER_MS)
  /** 토큰은 있으나 만료된 상태 여부 */
  const isExpired = computed(() => remainingMs.value === 0 && !!localStorage.getItem('fg_access_token'))

  return { formatted, remainingSeconds, isVisible, isWarning, isDanger, isExpired }
}
