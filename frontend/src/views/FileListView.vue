<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useFileStore } from '../stores/file'
import { fileApi } from '../api/files'
import type { FileStatusNotification } from '../types/file'

const store = useFileStore()
let stompClient: Client | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

const STATUS_MAP: Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' }> = {
  UPLOADED:   { label: '업로드됨',  type: 'info'    },
  PROCESSING: { label: '처리중',    type: 'warning' },
  DONE:       { label: '완료',      type: 'success' },
  FAIL:       { label: '실패',      type: 'danger'  },
}

function connectWebSocket() {
  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 5000,
    onConnect: () => {
      const files = store.page?.content ?? []
      files.forEach(subscribeFile)
    },
  })
  stompClient.activate()
}

function subscribeFile(file: { id: number }) {
  stompClient?.subscribe(`/topic/files/${file.id}`, (msg) => {
    const notification: FileStatusNotification = JSON.parse(msg.body)
    store.updateStatus(notification.fileId, notification.status)
    if (notification.status !== 'PROCESSING') stopPolling()
  })
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    const hasProcessing = store.page?.content.some((f) => f.status === 'PROCESSING')
    if (hasProcessing) {
      await store.fetchList(store.page?.number ?? 0)
      store.page?.content.filter((f) => f.status === 'PROCESSING').forEach(subscribeFile)
    } else {
      stopPolling()
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

async function loadPage(pageNum: number) {
  await store.fetchList(pageNum)
  const processing = store.page?.content.filter((f) => f.status === 'PROCESSING') ?? []
  processing.forEach(subscribeFile)
  if (processing.length > 0) startPolling()
  else stopPolling()
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('파일을 삭제하시겠습니까?', '확인', { type: 'warning' })
  await fileApi.delete(id)
  ElMessage.success('삭제되었습니다.')
  loadPage(store.page?.number ?? 0)
}

async function handleDownload(id: number, name: string) {
  const res = await fetch(`/api/v1/files/${id}/download`)
  if (!res.ok) { ElMessage.error('다운로드 실패'); return }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = name; a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  loadPage(0)
  connectWebSocket()
})
onUnmounted(() => {
  stompClient?.deactivate()
  stopPolling()
})
</script>

<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>파일 처리 목록</span>
          <el-button size="small" @click="loadPage(store.page?.number ?? 0)">새로고침</el-button>
        </div>
      </template>

      <el-table
        v-loading="store.loading"
        :data="store.page?.content"
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="originalName" label="파일명" min-width="200" show-overflow-tooltip />
        <el-table-column label="크기" width="100">
          <template #default="{ row }">
            {{ (row.fileSize / 1024).toFixed(1) }} KB
          </template>
        </el-table-column>
        <el-table-column label="상태" width="110">
          <template #default="{ row }">
            <el-tag :type="STATUS_MAP[row.status]?.type ?? 'info'">
              {{ STATUS_MAP[row.status]?.label ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="업로드 시각" width="170">
          <template #default="{ row }">
            {{ new Date(row.createdAt).toLocaleString('ko-KR') }}
          </template>
        </el-table-column>
        <el-table-column label="작업" width="160">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DONE'"
              type="primary"
              size="small"
              @click="handleDownload(row.id, row.originalName)"
            >
              다운로드
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(row.id)"
            >
              삭제
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: center; margin-top: 16px;">
        <el-pagination
          v-if="store.page && store.page.totalPages > 1"
          :current-page="(store.page.number ?? 0) + 1"
          :page-size="store.page.size"
          :total="store.page.totalElements"
          layout="prev, pager, next"
          @current-change="(p: number) => loadPage(p - 1)"
        />
      </div>
    </el-card>
  </div>
</template>
