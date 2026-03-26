<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { fileApi } from '../api/files'
import type { FileUploadResponse } from '../types/file'

const router = useRouter()
const uploading = ref(false)
const progress = ref(0)
const result = ref<FileUploadResponse | null>(null)

const ALLOWED = ['docx', 'xlsx', 'pptx']

function getExt(name: string) {
  return name.split('.').pop()?.toLowerCase() ?? ''
}

async function handleUpload(file: File) {
  const ext = getExt(file.name)
  if (!ALLOWED.includes(ext)) {
    ElMessage.error(`허용되지 않는 파일 형식입니다. (허용: ${ALLOWED.join(', ')})`)
    return false
  }

  uploading.value = true
  progress.value = 0
  result.value = null

  try {
    const res = await fileApi.upload(file, (p) => { progress.value = p })
    result.value = res.data.data
    ElMessage.success('업로드 완료! CDR 처리가 시작되었습니다.')
  } finally {
    uploading.value = false
  }
  return false
}
</script>

<template>
  <div class="upload-view">
    <el-card>
      <template #header>
        <span>파일 업로드</span>
      </template>

      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        :on-change="(f: any) => handleUpload(f.raw)"
        accept=".docx,.xlsx,.pptx"
      >
        <el-icon :size="48" style="color: #409eff"><UploadFilled /></el-icon>
        <div style="margin-top: 12px; font-size: 14px; color: #606266">
          파일을 드래그하거나 클릭하여 업로드
        </div>
        <div style="font-size: 12px; color: #909399; margin-top: 4px">
          지원 형식: .docx, .xlsx, .pptx / 최대 100MB
        </div>
      </el-upload>

      <el-progress
        v-if="uploading"
        :percentage="progress"
        style="margin-top: 16px"
      />

      <el-alert
        v-if="result"
        type="success"
        style="margin-top: 16px"
        :closable="false"
      >
        <template #title>
          업로드 완료 — <strong>{{ result.originalName }}</strong>
          (ID: {{ result.fileId }}, 상태: {{ result.status }})
        </template>
        <el-button
          type="primary"
          size="small"
          style="margin-top: 8px"
          @click="router.push('/files')"
        >
          처리 목록에서 확인
        </el-button>
      </el-alert>
    </el-card>
  </div>
</template>

<style scoped>
.upload-view { max-width: 640px; margin: 0 auto; }
</style>
