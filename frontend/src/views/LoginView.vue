<!--
  LoginView
  - 사용자 로그인 페이지
  - 아이디/비밀번호 입력 폼과 유효성 검사를 처리한다
  - Caps Lock 감지 및 비밀번호 가시성 토글 기능을 제공한다
  - 브라우저 자동완성(autofill) 감지를 통해 부드러운 렌더링을 보장한다
  - 데모 계정 버튼으로 역할별 빠른 로그인을 지원한다
  - 로그인 성공 시 redirect 쿼리 파라미터 또는 루트 경로로 이동한다
-->
<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue' // computed 추가
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'
// 아이콘 추가 임포트
import { User, Lock, Warning, View, Hide } from '@element-plus/icons-vue'
import ThemeToggle from '../components/ThemeToggle.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

/** 로그인 API 호출 중 로딩 상태 */
const loading = ref(false)

/** 로그인 폼 데이터 (아이디, 비밀번호) */
const form = reactive({ username: '', password: '' })

/** Element Plus 폼 인스턴스 참조 (유효성 검사 호출용) */
const formRef = ref()

// ── Caps Lock / 비밀번호 가시성 ────────────────
/** Caps Lock 활성화 여부 */
const isCapsLockOn = ref(false)

/** 비밀번호 입력 필드의 텍스트 가시성 여부 */
const isPasswordVisible = ref(false)

/** 비밀번호 가시성 상태에 따라 input type을 동적으로 반환한다 */
const passwordType = computed(() => isPasswordVisible.value ? 'text' : 'password')

const rules = {
  username: [{ required: true, message: '아이디를 입력하세요.', trigger: 'blur' }],
  password: [{ required: true, message: '비밀번호를 입력하세요.', trigger: 'blur' }],
}

/**
 * 비밀번호 입력 필드의 텍스트 가시성을 토글한다.
 */
const togglePasswordVisibility = () => {
  isPasswordVisible.value = !isPasswordVisible.value
}

/**
 * 키보드/마우스 이벤트에서 Caps Lock 상태를 감지하여 상태를 갱신한다.
 *
 * @param event KeyboardEvent 또는 MouseEvent
 */
const checkCapsLock = (event: any) => {
  if (event instanceof KeyboardEvent) {
    isCapsLockOn.value = event.getModifierState("CapsLock")
  }
}

/**
 * 브라우저 자동완성(autofill) CSS 애니메이션 이벤트를 감지하여
 * Vue 반응형 바인딩이 자동완성 값을 인식하도록 input 이벤트를 강제 발생시킨다.
 *
 * @param e AnimationEvent (loginAutofill 애니메이션 이름으로 판별)
 */
function onAutofillDetected(e: Event) {
  const anim = e as AnimationEvent
  if (anim.animationName === 'loginAutofill') {
    // 아주 짧은 지연을 주어 브라우저의 기본 처리가 끝난 뒤 실행되게 함
    setTimeout(() => {
      ;(e.target as HTMLInputElement).dispatchEvent(new Event('input', { bubbles: true }))
    }, 10)
  }
}

/** 패널 페이드인 렌더링 준비 완료 여부 (자동완성 대기 후 true 로 전환) */
const isReady = ref(false)
onMounted(() => {

  // 브라우저 자동완성 완료 대기 -> 부드러운 화면 렌더링 경험험
  setTimeout(() => { isReady.value = true }, 100)

  window.addEventListener('keydown', checkCapsLock)
  window.addEventListener('keyup', checkCapsLock)
  window.addEventListener('mousedown', checkCapsLock)
  document.addEventListener('animationstart', onAutofillDetected, true)
})

onUnmounted(() => {
  window.removeEventListener('keydown', checkCapsLock)
  window.removeEventListener('keyup', checkCapsLock)
  window.removeEventListener('mousedown', checkCapsLock)
  document.removeEventListener('animationstart', onAutofillDetected, true)
})

/**
 * 폼 유효성 검사를 수행하고 로그인 API를 호출한다.
 * 성공 시 redirect 경로 또는 루트('/')로 이동하며,
 * 실패 시 오류 메시지를 ElMessage로 표시한다.
 */
async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    loading.value = true
    await authStore.login(form.username, form.password)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (err: any) {
    if (err.username || err.password) return;
    const msg = err?.response?.data?.error?.message ?? '아이디 또는 비밀번호를 확인해주세요.'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

/**
 * 데모 계정 정보를 폼에 채워 넣고 즉시 로그인을 시도한다.
 *
 * @param username 데모 계정 아이디
 * @param password 데모 계정 비밀번호
 */
function loginAsDemo(username: string, password: string) {
  form.username = username
  form.password = password
  handleLogin()
}

// ── 데모 계정 목록 ─────────────────────────────
const demoUsers = [
  { label: '관리자', username: 'admin', password: 'password123', type: 'danger' as const, description: '전체 권한' },
  { label: '감사자', username: 'auditor', password: 'password123', type: 'warning' as const, description: '로그 열람' },
  { label: '사용자', username: 'user1', password: 'password123', type: 'success' as const, description: '일반 이용' },
]
</script>

<template>
  <div class="login-wrapper">
    <div class="login-panel" :class="{ 'is-ready': isReady }">
      <div class="brand">
        <div class="brand-logo">File Gateway</div>
        <div class="brand-sub">CDR · Content Disarm &amp; Reconstruction</div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <div class="form-top-row">
          <ThemeToggle />
        </div>

        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="아이디 입력"
            :prefix-icon="User"
            size="large"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password" class="password-item">
          <!-- show-password 속성을 빼고 :type을 동적으로 제어 -->
          <el-input
            v-model="form.password"
            :type="passwordType"
            placeholder="비밀번호"
            :prefix-icon="Lock"
            size="large"
            @keyup.enter="handleLogin"
          >
            <!-- [언제나 떠 있는 눈 아이콘 슬롯] -->
            <template #suffix>
              <el-icon class="eye-icon" @click="togglePasswordVisibility">
                <component :is="isPasswordVisible ? View : Hide" />
              </el-icon>
            </template>
          </el-input>

          <div class="caps-lock-floating-area">
            <transition name="el-fade-in">
              <span v-show="isCapsLockOn" class="caps-lock-warning">
                <el-icon><Warning /></el-icon> Caps Lock이 켜져 있습니다.
              </span>
            </transition>
          </div>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="login-btn"
          @click="handleLogin"
        >
          로그인
        </el-button>
      </el-form>

      <div class="demo-section">
        <div class="demo-divider">
          <span>데모 계정으로 빠른 로그인</span>
        </div>
        <div class="demo-buttons">
          <el-tooltip
            v-for="demo in demoUsers"
            :key="demo.username"
            :content="demo.description"
            placement="bottom"
          >
            <div class="demo-btn-wrapper">
              <el-button
                :type="demo.type"
                size="default"
                plain
                class="demo-btn"
                @click="loginAsDemo(demo.username, demo.password)"
              >
                {{ demo.label }}
              </el-button>
            </div>
          </el-tooltip>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-fill-color-blank);
  transition: background-color 0.25s;
}

.login-panel {
  width: 370px;
  background: var(--bg-surface);
  background-color: var(--el-bg-color-overlay);
  border-radius: 14px;
  padding: 40px 24px;
  border: 1px solid var(--el-border-color-lighter);
  transition: background 0.25s, border-color 0.25s;
  opacity: 0;
  transition: opacity 0.3s ease;
}
.login-panel.is-ready {
  opacity: 1;
}

.brand {
  text-align: center;
  margin-bottom: 50px;
}
.brand-logo {
  font-family: 'Poppins', sans-serif;
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
}
.brand-sub {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

.form-top-row {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
  margin-top: -8px;
}

:deep(.el-input__wrapper) {
  height: 42px !important;      /* 박스 높이를 약간 확보 */
  padding: 0 15px !important;   /* 좌우 여백 */
  box-shadow: 0 0 0 1px #dcdfe6 inset !important;
  border-radius: 8px;
  display: flex;
  align-items: center;          /* 내부 요소 수직 중앙 정렬 */
}

/* 2. 실제 입력되는 텍스트(글자) 스타일 */
:deep(.el-input__inner) {
  font-size: 14px !important;   /* 글자 크기를 16px -> 14px로 줄여서 테두리와 간격 확보 */
  height: 20px !important;      /* 글자가 차지하는 높이를 제한 */
  line-height: 20px !important; /* 글자 높이와 line-height를 맞춰서 정중앙 배치 */
  color: #303133;
  /* 위아래 간격(Margin)을 강제로 주어 테두리 침범 방지 */
  margin: 4px 0 !important;
}

/* 3. 아이콘 크기 조절 (글자가 작아졌으니 아이콘도 비율 맞춤) */
:deep(.el-input__prefix-inner),
:deep(.el-input__suffix-inner) {
  font-size: 16px !important;
  display: flex;
  align-items: center;
}

/* 4. 포커스 시 테두리 색상 */
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset !important;
}
/* 자동완성 스타일 */
:deep(input:-webkit-autofill) {
  -webkit-box-shadow: 0 0 0 1000px var(--bg-surface) inset !important;
  transition: background-color 5000s ease-in-out 0s;
  font-size: 13px !important;
  font-family: 'Noto Sans KR', sans-serif !important;
}

/* 눈 아이콘 스타일링 */
.eye-icon {
  cursor: pointer;
  color: #a8abb2;
  font-size: 18px;
  transition: color 0.2s;
}
.eye-icon:hover {
  color: var(--el-color-primary);
}

/* 레이아웃 고정 핵심 스타일 */
.password-item {
  margin-bottom: 80px !important;
  position: relative;
}

.caps-lock-floating-area {
  position: absolute;
  top: 100%;
  left: 0;
  width: 100%;
  height: 30px;
  display: flex;
  align-items: center;
  z-index: 10;
}

.caps-lock-warning {
  color: #f56c6c;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 100px;
}

.login-btn {
  width: 100%;
  height: 48px !important;
  font-weight: 600;
  border-radius: 8px;
}

.demo-section {
  margin-top: 32px;
}
.demo-divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  color: #c0c4cc;
  font-size: 11px;
}
.demo-divider::before,
.demo-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #ebeef5;
}
.demo-buttons {
  display: flex;
  gap: 8px;
}
.demo-btn-wrapper {
  flex: 1;
}
.demo-btn {
  width: 100%;
  font-size: 12px !important;
}
</style>
