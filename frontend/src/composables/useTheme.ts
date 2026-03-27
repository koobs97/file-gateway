import { ref } from 'vue'

const isDark = ref(localStorage.getItem('fg-theme') === 'dark')

function apply(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
  localStorage.setItem('fg-theme', dark ? 'dark' : 'light')
}

// 앱 로드 시 즉시 적용
apply(isDark.value)

export function useTheme() {
  function toggleTheme() {
    isDark.value = !isDark.value
    apply(isDark.value)
  }

  return { isDark, toggleTheme }
}
