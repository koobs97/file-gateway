import { ref, computed, onMounted, onUnmounted } from 'vue'

function parseJwtExpiry(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp * 1000
  } catch {
    return null
  }
}

const WARN_MS  = 5 * 60 * 1000   // 5분 미만 → 주의
const DANGER_MS = 2 * 60 * 1000  // 2분 미만 → 위험
const SHOW_MS  = 30 * 60 * 1000  // 30분 미만부터 표시

export function useSessionTimer() {
  const remainingMs = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  function update() {
    const token = localStorage.getItem('fg_access_token')
    if (!token) { remainingMs.value = 0; return }
    const expiry = parseJwtExpiry(token)
    if (!expiry) { remainingMs.value = 0; return }
    remainingMs.value = Math.max(0, expiry - Date.now())
  }

  onMounted(() => { update(); timer = setInterval(update, 1000) })
  onUnmounted(() => { if (timer) clearInterval(timer) })

  const remainingSeconds = computed(() => Math.floor(remainingMs.value / 1000))

  const formatted = computed(() => {
    const s = remainingSeconds.value
    const h = Math.floor(s / 3600)
    const m = Math.floor((s % 3600) / 60)
    const sec = s % 60
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
    return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  })

  const isVisible = computed(() => remainingMs.value > 0 && remainingMs.value < SHOW_MS)
  const isWarning = computed(() => remainingMs.value > 0 && remainingMs.value < WARN_MS)
  const isDanger  = computed(() => remainingMs.value > 0 && remainingMs.value < DANGER_MS)
  const isExpired = computed(() => remainingMs.value === 0 && !!localStorage.getItem('fg_access_token'))

  return { formatted, remainingSeconds, isVisible, isWarning, isDanger, isExpired }
}
