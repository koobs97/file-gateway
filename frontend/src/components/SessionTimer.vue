<!--
  SessionTimer
  - JWT 세션 만료 시간을 표시하는 헤더 컴포넌트
  - useSessionTimer 컴포저블에서 남은 시간, 경고/위험 상태, 만료 여부를 구독한다
  - 남은 시간이 5분이 되면 ElNotification으로 경고를 한 번만 표시한다
  - 세션이 만료되면 토큰을 정리하고 /login 으로 리다이렉트한다
  - '연장' 버튼 클릭 시 리프레시 토큰으로 세션 연장을 시도한다
  - 세션이 표시 임계값 이하일 때만 렌더링된다 (isVisible)
-->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification, ElMessage } from 'element-plus'
import { Timer, Warning, RefreshRight } from '@element-plus/icons-vue'
import { useSessionTimer } from '../composables/useSessionTimer'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

/** useSessionTimer 컴포저블에서 제공하는 세션 타이머 상태 */
const { formatted, isVisible, isWarning, isDanger, isExpired, remainingSeconds } = useSessionTimer()

/** 세션 연장 API 호출 중 로딩 상태 */
const extending = ref(false)

/**
 * 리프레시 토큰으로 세션 연장을 시도한다.
 * 성공 시 성공 메시지를, 실패 시 오류 메시지를 표시한다.
 */
async function handleExtend() {
  extending.value = true
  const ok = await authStore.tryRefresh()
  extending.value = false
  if (ok) {
    ElMessage({ message: '세션이 연장되었습니다.', type: 'success', duration: 2000 })
  } else {
    ElMessage({ message: '세션 연장 실패. 다시 로그인해 주세요.', type: 'error', duration: 3000 })
  }
}

// ── 세션 경고 알림 (5분) ──────────────────────
/** 5분 경고 알림 중복 방지 플래그 */
let warnedOnce = false

// 5분 남았을 때 한 번만 경고
watch(remainingSeconds, (s) => {
  if (s === 300 && !warnedOnce) {
    warnedOnce = true
    ElNotification({
      title: '세션 만료 임박',
      message: '5분 후 자동 로그아웃됩니다. 세션을 연장하거나 작업을 저장해 주세요.',
      type: 'warning',
      duration: 10000,
    })
  }
})

// ── 세션 만료 처리 ────────────────────────────
// 만료 시 정리 후 로그인 이동
watch(isExpired, (expired) => {
  if (!expired) return
  authStore.clearTokens()
  ElNotification({
    title: '세션 만료',
    message: '로그인 세션이 만료되었습니다. 다시 로그인해 주세요.',
    type: 'error',
    duration: 4000,
  })
  setTimeout(() => {
    ElNotification.closeAll()
    router.push('/login')
  }, 1500)
})
</script>

<template>
  <div v-if="isVisible" class="session-timer" :class="{ 'is-warning': isWarning, 'is-danger': isDanger }">
    <el-icon class="timer-icon">
      <Warning v-if="isWarning" />
      <Timer v-else />
    </el-icon>
    <span class="timer-text">{{ formatted }}</span>
    <button
      type="button"
      class="extend-btn"
      :class="{ 'is-loading': extending }"
      :disabled="extending"
      title="세션 연장"
      @click="handleExtend"
    >
      <el-icon :class="{ 'is-loading': extending }"><RefreshRight /></el-icon>
      연장
    </button>
  </div>
</template>

<style scoped>
.session-timer {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 4px 3px 9px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.3px;
  border: 1px solid var(--bg-border);
  background: var(--bg-muted);
  color: var(--text-muted);
  transition: background 0.3s, border-color 0.3s, color 0.3s;
  white-space: nowrap;
}

.session-timer.is-warning {
  background: rgba(240, 160, 32, 0.1);
  border-color: rgba(240, 160, 32, 0.35);
  color: var(--color-warning);
}

.session-timer.is-danger {
  background: rgba(208, 48, 80, 0.1);
  border-color: rgba(208, 48, 80, 0.35);
  color: var(--color-danger);
  animation: pulse-danger 1s ease-in-out infinite;
}

.timer-icon {
  font-size: 13px;
}

.timer-text {
  min-width: 36px;
}

/* 연장 버튼 */
.extend-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: var(--bg-surface);
  border-radius: 5px;
  padding: 3px 7px;
  font-size: 11px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  color: var(--text-secondary);
  border: 1px solid var(--bg-border);
  transition: background 0.15s, color 0.15s, border-color 0.15s;
  line-height: 1;
}

.extend-btn:hover:not(:disabled) {
  background: var(--primary);
  color: #fff;
  border-color: var(--primary);
}

.extend-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.session-timer.is-warning .extend-btn {
  color: var(--color-warning);
  border-color: rgba(240, 160, 32, 0.4);
  background: rgba(240, 160, 32, 0.08);
}
.session-timer.is-warning .extend-btn:hover:not(:disabled) {
  background: var(--color-warning);
  color: #fff;
  border-color: var(--color-warning);
}

.session-timer.is-danger .extend-btn {
  color: var(--color-danger);
  border-color: rgba(208, 48, 80, 0.4);
  background: rgba(208, 48, 80, 0.08);
}
.session-timer.is-danger .extend-btn:hover:not(:disabled) {
  background: var(--color-danger);
  color: #fff;
  border-color: var(--color-danger);
}

@keyframes pulse-danger {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.55; }
}
</style>
