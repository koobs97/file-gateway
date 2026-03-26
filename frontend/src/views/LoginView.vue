<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const form = reactive({ username: '', password: '' })
const formRef = ref()

const rules = {
  username: [{ required: true, message: '아이디를 입력하세요.', trigger: 'blur' }],
  password: [{ required: true, message: '비밀번호를 입력하세요.', trigger: 'blur' }],
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}

// 데모 계정 정보
const demoUsers = [
  {
    label: '관리자',
    username: 'admin',
    password: 'password123',
    role: 'ROLE_ADMIN',
    type: 'danger' as const,
    description: '전체 권한 (업로드·삭제·관리·감사)',
  },
  {
    label: '감사자',
    username: 'auditor',
    password: 'password123',
    role: 'ROLE_AUDITOR',
    type: 'warning' as const,
    description: '조회·다운로드·감사 로그 열람',
  },
  {
    label: '일반 사용자',
    username: 'user1',
    password: 'password123',
    role: 'ROLE_END_USER',
    type: 'success' as const,
    description: '업로드·목록·다운로드',
  },
]

async function loginAsDemo(username: string, password: string) {
  loading.value = true
  try {
    await authStore.login(username, password)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrapper">
    <el-card class="login-card" shadow="always">
      <div class="login-header">
        <div class="login-logo">File Gateway CDR</div>
        <div class="login-subtitle">안전한 파일 무해화 시스템</div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="아이디" prop="username">
          <el-input
            v-model="form.username"
            placeholder="아이디 입력"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item label="비밀번호" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="비밀번호 입력"
            prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          style="width: 100%; margin-top: 8px;"
          @click="handleLogin"
        >
          로그인
        </el-button>
      </el-form>

      <!-- 데모 모드 -->
      <el-divider content-position="center">
        <span class="demo-label">데모 계정으로 빠른 로그인</span>
      </el-divider>

      <div class="demo-section">
        <el-tooltip
          v-for="demo in demoUsers"
          :key="demo.username"
          :content="demo.description"
          placement="bottom"
        >
          <el-button
            :type="demo.type"
            size="default"
            plain
            :loading="loading"
            class="demo-btn"
            @click="loginAsDemo(demo.username, demo.password)"
          >
            {{ demo.label }}
          </el-button>
        </el-tooltip>
      </div>

      <div class="demo-hint">
        <el-icon><InfoFilled /></el-icon>
        각 버튼을 클릭하면 해당 역할로 즉시 로그인됩니다
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 420px;
  border-radius: 12px;
}
.login-header {
  text-align: center;
  margin-bottom: 28px;
}
.login-logo {
  font-size: 22px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 6px;
}
.login-subtitle {
  font-size: 13px;
  color: #909399;
}
.demo-label {
  font-size: 12px;
  color: #909399;
}
.demo-section {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-bottom: 12px;
}
.demo-btn {
  flex: 1;
}
.demo-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #c0c4cc;
  justify-content: center;
}
</style>
