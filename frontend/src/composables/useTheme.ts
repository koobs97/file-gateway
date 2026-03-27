/**
 * 테마(다크/라이트 모드) 컴포저블
 * - localStorage에서 테마 설정을 읽어 앱 초기 로드 시 즉시 적용
 * - toggleTheme 호출로 다크·라이트 모드 전환 및 localStorage 저장
 * - html 요소의 'dark' 클래스 토글을 통해 Element Plus 다크 테마 제어
 */
import { ref } from 'vue'

/** 현재 다크 모드 활성화 여부 */
const isDark = ref(localStorage.getItem('fg-theme') === 'dark')

/**
 * 다크 모드 클래스를 DOM에 적용하고 설정을 localStorage에 저장
 *
 * @param dark true이면 다크 모드, false이면 라이트 모드
 */
function apply(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
  localStorage.setItem('fg-theme', dark ? 'dark' : 'light')
}

// 앱 로드 시 즉시 적용
apply(isDark.value)

/**
 * 테마 컴포저블 훅
 *
 * @returns isDark (현재 다크 모드 여부 ref), toggleTheme (모드 전환 함수)
 */
export function useTheme() {
  /**
   * 다크/라이트 모드를 전환하고 설정을 지속
   */
  function toggleTheme() {
    isDark.value = !isDark.value
    apply(isDark.value)
  }

  return { isDark, toggleTheme }
}
