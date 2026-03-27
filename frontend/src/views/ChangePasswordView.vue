<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const formRef = ref()
const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = {
  currentPassword: [{ required: true, message: '현재 비밀번호를 입력하세요.', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '새 비밀번호를 입력하세요.', trigger: 'blur' },
    { min: 8, message: '8자 이상 입력하세요.', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '비밀번호를 한 번 더 입력하세요.', trigger: 'blur' },
    {
      validator: (_: any, value: string, callback: Function) => {
        if (value !== form.newPassword) callback(new Error('새 비밀번호가 일치하지 않습니다.'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    loading.value = true
    await authApi.changePassword(form.currentPassword, form.newPassword)
    // 서버에서 passwordChanged=true 로 갱신됐으므로 /me 재조회
    await authStore.fetchMe()
    ElMessage.success('비밀번호가 변경되었습니다.')
    router.push('/')
  } catch (err: any) {
    if (err.currentPassword || err.newPassword || err.confirmPassword) return
    const msg = err?.response?.data?.error?.message ?? '비밀번호 변경에 실패했습니다.'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="change-pw-wrapper">
    <div class="change-pw-panel">
      <div class="brand">
        <div class="brand-logo">File Gateway</div>
        <div class="brand-sub">비밀번호 변경 필요</div>
      </div>

      <div class="notice">
        <el-icon><Lock /></el-icon>
        관리자가 생성한 계정입니다. 첫 로그인 시 비밀번호를 변경해야 합니다.
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="change-pw-form"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="현재 비밀번호" prop="currentPassword">
          <el-input
            v-model="form.currentPassword"
            type="password"
            placeholder="현재 비밀번호 입력"
            :prefix-icon="Lock"
            show-password
            size="large"
          />
        </el-form-item>

        <el-form-item label="새 비밀번호" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="8자 이상"
            :prefix-icon="Lock"
            show-password
            size="large"
          />
        </el-form-item>

        <el-form-item label="새 비밀번호 확인" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="새 비밀번호 재입력"
            :prefix-icon="Lock"
            show-password
            size="large"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="submit-btn"
          @click="handleSubmit"
        >
          비밀번호 변경
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.change-pw-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-bg-color);
}

.change-pw-panel {
  width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 40px 28px;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.06);
}

.brand {
  text-align: center;
  margin-bottom: 28px;
}
.brand-logo {
  font-family: 'Poppins', sans-serif;
  font-size: 22px;
  font-weight: 700;
  color: #001233;
}
.brand-sub {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

.notice {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 12px;
  color: #8a6d3b;
  margin-bottom: 24px;
  line-height: 1.5;
}

.change-pw-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.submit-btn {
  width: 100%;
  height: 48px !important;
  font-weight: 600;
  border-radius: 8px;
  margin-top: 8px;
}
</style>
