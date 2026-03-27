<!--
  ThemeToggle
  - 라이트/다크 모드 전환 버튼 컴포넌트
  - useTheme 컴포저블의 isDark 상태에 따라 아이콘(Sunny/Moon)을 전환한다
  - 버튼 클릭 시 toggleTheme()을 호출하여 테마를 즉시 변경한다
  - 아이콘 전환에는 out-in 트랜지션 애니메이션을 적용한다
-->
<script setup lang="ts">
import { useTheme } from '../composables/useTheme'
import { Sunny, Moon } from '@element-plus/icons-vue'

/** useTheme 컴포저블에서 현재 다크 모드 여부와 토글 함수를 가져온다 */
const { isDark, toggleTheme } = useTheme()
</script>

<template>
  <button
    type="button"
    class="theme-toggle"
    :title="isDark ? '라이트 모드로 전환' : '다크 모드로 전환'"
    @click="toggleTheme"
  >
    <div class="icon-wrapper">
      <transition name="icon-flip" mode="out-in">
        <el-icon v-if="isDark" key="sun"><Sunny /></el-icon>
        <el-icon v-else key="moon"><Moon /></el-icon>
      </transition>
    </div>
  </button>
</template>

<style scoped>
.theme-toggle {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  background-color: var(--el-fill-color-blank);
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.theme-toggle:hover {
  background-color: var(--el-fill-color-light);
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

/* 아이콘 전환 애니메이션 - 더 부드럽게 개선 */
.icon-flip-enter-active,
.icon-flip-leave-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.icon-flip-enter-from {
  opacity: 0;
  transform: translateY(20px) rotate(-120deg);
}

.icon-flip-leave-to {
  opacity: 0;
  transform: translateY(-20px) rotate(120deg);
}

/* 다크모드에서 버튼 내부 광원 효과 (선택 사항) */
html.dark .theme-toggle:hover {
  box-shadow: 0 0 15px rgba(255, 255, 255, 0.05);
}
</style>
