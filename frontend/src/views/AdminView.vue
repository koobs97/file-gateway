<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fileApi } from '../api/files'
import type { FileStatistics } from '../types/file'

const stats = ref<FileStatistics | null>(null)
const loading = ref(false)

async function loadStats() {
  loading.value = true
  try {
    const res = await fileApi.getStatistics()
    stats.value = res.data.data
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>

<template>
  <div v-loading="loading">
    <el-row :gutter="16" style="margin-bottom: 24px;">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">활성 파일</div>
            <div class="stat-value">{{ stats?.total ?? '-' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">성공 (DONE)</div>
            <div class="stat-value success">{{ stats?.done ?? '-' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">실패 (FAIL)</div>
            <div class="stat-value danger">{{ stats?.fail ?? '-' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">삭제됨</div>
            <div class="stat-value deleted">{{ stats?.deleted ?? '-' }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between;">
          <span>통계 요약</span>
          <el-button size="small" @click="loadStats">새로고침</el-button>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="활성 파일">{{ stats?.total ?? '-' }} 건</el-descriptions-item>
        <el-descriptions-item label="처리중">{{ stats?.processing ?? '-' }} 건</el-descriptions-item>
        <el-descriptions-item label="무해화 완료">{{ stats?.done ?? '-' }} 건</el-descriptions-item>
        <el-descriptions-item label="처리 실패">{{ stats?.fail ?? '-' }} 건</el-descriptions-item>
        <el-descriptions-item label="삭제됨">{{ stats?.deleted ?? '-' }} 건</el-descriptions-item>
        <el-descriptions-item label="성공률">{{ stats != null ? stats.successRate.toFixed(1) + '%' : '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.stat-card { text-align: center; padding: 8px 0; }
.stat-label { font-size: 13px; color: #909399; margin-bottom: 8px; }
.stat-value { font-size: 32px; font-weight: 700; color: #303133; }
.stat-value.success  { color: #67c23a; }
.stat-value.danger   { color: #f56c6c; }
.stat-value.deleted  { color: #909399; }
</style>
