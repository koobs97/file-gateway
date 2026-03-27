<!--
  FileListView
  - CDR 파일 처리 목록 페이지
  - 파일명/상태 필터 검색과 페이지네이션을 지원한다
  - 행 클릭 시 오른쪽 Drawer에서 파일 상세 정보와 이벤트 이력을 확인한다
  - ADMIN 권한자는 단건/일괄 삭제를 수행할 수 있다
  - DONE 상태 파일의 무해화 결과물을 단건/일괄 다운로드할 수 있다
  - WebSocket(STOMP)으로 PROCESSING 파일의 상태 변경을 실시간으로 반영한다
  - 폴링(2초 간격)으로 WebSocket 미수신 상황을 보완한다
-->
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Warning, Loading, Search, InfoFilled } from '@element-plus/icons-vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useFileStore } from '../stores/file'
import { useAuthStore } from '../stores/auth'
import { fileApi } from '../api/files'
import type { FileStatusNotification, FileDetailResponse, FileEvent } from '../types/file'

const store = useFileStore()
const authStore = useAuthStore()

/** STOMP 클라이언트 인스턴스 */
let stompClient: Client | null = null

/** 폴링 타이머 참조 (clearInterval 용) */
let pollTimer: ReturnType<typeof setInterval> | null = null

// ── 검색 필터 ─────────────────────────────────
/** 파일명 검색 키워드 */
const keyword = ref('')

/** 처리 상태 필터 선택값 */
const statusFilter = ref('')

/** 상태 필터 드롭다운 옵션 목록 */
const STATUS_OPTIONS = [
  { label: '전체', value: '' },
  { label: '처리중', value: 'PROCESSING' },
  { label: '완료', value: 'DONE' },
  { label: '실패', value: 'FAIL' },
  { label: '업로드됨', value: 'UPLOADED' },
]

// ── 상태 매핑 ─────────────────────────────────
/** 처리 상태 코드를 한국어 레이블 및 Element Plus 태그 타입으로 매핑한다 */
const STATUS_MAP: Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' }> = {
  UPLOADED:   { label: '업로드됨', type: 'info' },
  PROCESSING: { label: '처리중',   type: 'warning' },
  DONE:       { label: '완료',     type: 'success' },
  FAIL:       { label: '실패',     type: 'danger' },
}

// ── 일괄 작업 ─────────────────────────────────
/** 테이블 체크박스로 선택된 파일 목록 */
const selected = ref<FileDetailResponse[]>([])

/** 일괄 삭제 처리 중 여부 */
const bulkDeleting = ref(false)

/**
 * 테이블 체크박스 선택 변경 이벤트 핸들러.
 *
 * @param rows 현재 선택된 행 목록
 */
function onSelectionChange(rows: FileDetailResponse[]) {
  selected.value = rows
}

// ── 삭제 확인 다이얼로그 ──────────────────────
/** 삭제 확인 다이얼로그 상태 객체 */
const deleteDialog = ref({
  visible: false,
  isBulk: false,
  targetId: null as number | null,
  targetName: '',
  deleting: false,
})

/**
 * 단건 삭제 확인 다이얼로그를 열고 대상 파일 정보를 설정한다.
 *
 * @param id 삭제 대상 파일 ID
 * @param name 삭제 대상 파일명
 */
function openDeleteDialog(id: number, name: string) {
  deleteDialog.value = { visible: true, isBulk: false, targetId: id, targetName: name, deleting: false }
}

/**
 * 일괄 삭제 확인 다이얼로그를 열고 선택 파일 수를 표시한다.
 * 선택된 항목이 없으면 아무 동작도 하지 않는다.
 */
function openBulkDeleteDialog() {
  if (!selected.value.length) return
  deleteDialog.value = { visible: true, isBulk: true, targetId: null, targetName: '', deleting: false }
}

/**
 * 다이얼로그에서 삭제를 최종 확인하여 단건 또는 일괄 삭제를 수행한다.
 * 완료 후 현재 페이지를 새로고침하고 다이얼로그를 닫는다.
 */
async function confirmDelete() {
  deleteDialog.value.deleting = true
  try {
    if (deleteDialog.value.isBulk) {
      await Promise.all(selected.value.map((f) => fileApi.delete(f.id)))
      ElMessage.success(`${selected.value.length}개 파일이 삭제되었습니다.`)
      selected.value = []
    } else {
      await fileApi.delete(deleteDialog.value.targetId!)
      ElMessage.success('파일이 삭제되었습니다.')
    }
    loadPage(store.page?.number ?? 0)
    deleteDialog.value.visible = false
  } finally {
    deleteDialog.value.deleting = false
  }
}

/**
 * 선택된 파일 중 DONE 상태인 파일을 모두 순차적으로 다운로드한다.
 * DONE 상태 파일이 없으면 경고 메시지를 표시한다.
 */
async function handleBulkDownload() {
  const doneFiles = selected.value.filter((f) => f.status === 'DONE')
  if (!doneFiles.length) {
    ElMessage.warning('완료(DONE) 상태의 파일만 다운로드할 수 있습니다.')
    return
  }
  for (const f of doneFiles) {
    await handleDownload(f.id, f.originalName)
  }
}

// ── 단건 삭제/다운로드 ────────────────────────
/**
 * 단건 삭제 다이얼로그를 열어 삭제를 유도한다.
 *
 * @param id 삭제 대상 파일 ID
 * @param name 삭제 대상 파일명
 */
function handleDelete(id: number, name: string) {
  openDeleteDialog(id, name)
}

/**
 * 무해화된 파일을 서버에서 다운로드하여 브라우저 저장 대화상자를 트리거한다.
 * 파일명에 'sanitized_' 접두사를 추가한다.
 *
 * @param id 다운로드할 파일 ID
 * @param name 원본 파일명
 */
async function handleDownload(id: number, name: string) {
  try {
    const res = await fileApi.downloadFile(id)
    const blob = new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `sanitized_${name}`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('다운로드에 실패했습니다.')
  }
}

// ── 상세 Drawer ───────────────────────────────
/** 상세 Drawer 표시 여부 */
const drawerVisible = ref(false)

/** Drawer에 표시 중인 파일 상세 정보 */
const drawerFile = ref<FileDetailResponse | null>(null)

/** Drawer의 파일 이벤트 이력 목록 */
const drawerEvents = ref<FileEvent[]>([])

/** Drawer 이벤트 이력 로딩 상태 */
const drawerEventsLoading = ref(false)

/**
 * 파일 행 클릭 시 상세 Drawer를 열고 기본 정보를 설정한다.
 * ADMIN/AUDITOR 권한자인 경우 이벤트 이력도 추가 조회한다.
 *
 * @param row 클릭된 파일 행 데이터
 */
async function openDrawer(row: FileDetailResponse) {
  drawerFile.value = row
  drawerEvents.value = []
  drawerVisible.value = true

  if (authStore.canViewAudit) {
    drawerEventsLoading.value = true
    try {
      const res = await fileApi.getEvents(row.id)
      drawerEvents.value = res.data.data
    } catch {
      // 권한 없으면 무시
    } finally {
      drawerEventsLoading.value = false
    }
  }
}

/**
 * 이벤트 payload JSON 문자열을 읽기 쉬운 'key: value | ...' 형태로 포매팅한다.
 *
 * @param payload JSON 문자열 또는 null
 * @returns 포매팅된 문자열, 파싱 불가 시 원본 문자열, 빈 경우 '-'
 */
function formatEventPayload(payload: string | null): string {
  if (!payload) return '-'
  try {
    const obj = JSON.parse(payload)
    const entries = Object.entries(obj)
    if (!entries.length) return '-'
    return entries.map(([k, v]) => `${k}: ${v}`).join(' | ')
  } catch {
    return payload
  }
}

// ── WebSocket + Polling ───────────────────────
/**
 * STOMP WebSocket 클라이언트를 초기화하고 연결한다.
 * 연결 완료 시 현재 목록의 모든 파일에 대해 상태 알림을 구독한다.
 */
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

/**
 * 특정 파일의 WebSocket 상태 알림 토픽을 구독한다.
 * 알림 수신 시 store의 상태와 열려 있는 Drawer를 함께 업데이트한다.
 * PROCESSING 이외 상태로 전환되면 폴링을 중단한다.
 *
 * @param file id 를 가진 파일 객체
 */
function subscribeFile(file: { id: number }) {
  stompClient?.subscribe(`/topic/files/${file.id}`, (msg) => {
    const notification: FileStatusNotification = JSON.parse(msg.body)
    store.updateStatus(notification.fileId, notification.status)
    // Drawer 열려있으면 함께 업데이트
    if (drawerFile.value?.id === notification.fileId) {
      drawerFile.value = { ...drawerFile.value, status: notification.status }
    }
    if (notification.status !== 'PROCESSING') stopPolling()
  })
}

/**
 * 2초 간격의 폴링을 시작한다.
 * PROCESSING 파일이 없으면 폴링을 자동으로 중단한다.
 * 이미 폴링 중이면 중복 실행하지 않는다.
 */
function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    const hasProcessing = store.page?.content.some((f) => f.status === 'PROCESSING')
    if (hasProcessing) {
      await loadPage(store.page?.number ?? 0)
      store.page?.content.filter((f) => f.status === 'PROCESSING').forEach(subscribeFile)
    } else {
      stopPolling()
    }
  }, 2000)
}

/**
 * 폴링 타이머를 중단하고 초기화한다.
 */
function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

/**
 * 지정한 페이지 번호로 파일 목록을 조회하고 WebSocket 구독 및 폴링을 관리한다.
 *
 * @param pageNum 0-based 페이지 번호
 */
async function loadPage(pageNum: number) {
  await store.fetchList(pageNum, keyword.value || undefined, statusFilter.value || undefined)
  const processing = store.page?.content.filter((f) => f.status === 'PROCESSING') ?? []
  processing.forEach(subscribeFile)
  if (processing.length > 0) startPolling()
  else stopPolling()
}

/**
 * 현재 필터 조건으로 첫 페이지를 조회한다.
 */
function handleSearch() {
  loadPage(0)
}

/**
 * 검색 키워드와 상태 필터를 초기화하고 첫 페이지를 재조회한다.
 */
function handleReset() {
  keyword.value = ''
  statusFilter.value = ''
  loadPage(0)
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
  <div class="list-page-container">
    <el-card class="list-card">
      <template #header>
        <div class="list-header">
          <span>파일 처리 목록</span>
          <div class="list-header-right">
            <div class="row-hint">
              <el-icon class="row-hint-icon"><InfoFilled /></el-icon>
              <span>행 클릭 시 상세 정보 확인</span>
            </div>
            <el-button size="small" @click="loadPage(store.page?.number ?? 0)">새로고침</el-button>
          </div>
        </div>
      </template>

      <!-- 검색/필터 바 -->
      <div class="filter-bar">
        <div class="filter-left">
          <el-input
            v-model="keyword"
            placeholder="파일명 검색"
            clearable
            size="small"
            class="filter-input"
            @keyup.enter="handleSearch"
            @clear="handleReset"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select
            v-model="statusFilter"
            placeholder="상태"
            size="small"
            class="filter-select"
            clearable
            @change="handleSearch"
          >
            <el-option
              v-for="opt in STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-button type="primary" size="small" @click="handleSearch">검색</el-button>
          <el-button size="small" style="margin: 0;" @click="handleReset">초기화</el-button>
        </div>

        <!-- 일괄 작업 버튼 -->
        <div v-if="selected.length > 0" class="bulk-actions">
          <span class="bulk-count">{{ selected.length }}개 선택됨</span>
          <el-button
            v-if="authStore.isAdmin"
            type="danger"
            size="small"
            @click="openBulkDeleteDialog"
          >
            일괄 삭제
          </el-button>
          <el-button size="small" type="primary" style="margin: 0 0 0 8px;" @click="handleBulkDownload">
            일괄 다운로드
          </el-button>
        </div>
      </div>

      <!-- 테이블 -->
      <el-table
        v-loading="store.loading"
        :data="store.page?.content"
        height="100%"
        style="width: 100%"
        @selection-change="onSelectionChange"
        @row-click="openDrawer"
        row-class-name="clickable-row"
        stripe
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="originalName" label="파일명" min-width="200" show-overflow-tooltip />
        <el-table-column label="크기" width="90">
          <template #default="{ row }">
            {{ (row.fileSize / 1024).toFixed(1) }} KB
          </template>
        </el-table-column>

        <!-- 상태 + 인라인 진행 (E) -->
        <el-table-column label="상태" width="140">
          <template #default="{ row }">
            <div v-if="row.status === 'PROCESSING'" class="processing-cell">
              <el-progress
                :percentage="100"
                :striped="true"
                :striped-flow="true"
                :duration="8"
                :stroke-width="14"
                class="processing-bar"
              />
              <span class="processing-label">처리중</span>
            </div>
            <el-tag v-else :type="STATUS_MAP[row.status]?.type ?? 'info'" size="small">
              {{ STATUS_MAP[row.status]?.label ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="업로드 시각" width="180">
          <template #default="{ row }">
            {{ new Date(row.createdAt).toLocaleString('ko-KR') }}
          </template>
        </el-table-column>

        <el-table-column label="작업" width="170" @click.stop>
          <template #default="{ row }">
            <div class="action-cell">
              <el-button
                v-if="row.status === 'DONE'"
                type="primary"
                size="small"
                @click.stop="handleDownload(row.id, row.originalName)"
              >
                다운로드
              </el-button>
              <el-button
                v-if="authStore.isAdmin"
                type="danger"
                size="small"
                class="action-button-danger"
                @click.stop="handleDelete(row.id, row.originalName)"
              >
                삭제
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 페이징 -->
      <div class="pagination-wrap">
        <el-pagination
          v-if="store.page && store.page.totalPages > 1"
          :current-page="(store.page.number ?? 0) + 1"
          :page-size="store.page.size"
          :total="store.page.totalElements"
          layout="prev, pager, next, total"
          @current-change="(p: number) => loadPage(p - 1)"
        />
      </div>
    </el-card>

    <!-- 파일 상세 Drawer (C) -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerFile?.originalName ?? '파일 상세'"
      direction="rtl"
      size="480px"
    >
      <template v-if="drawerFile">
        <!-- 기본 정보 -->
        <el-descriptions :column="1" border size="small" class="drawer-desc">
          <el-descriptions-item label="파일 ID">{{ drawerFile.id }}</el-descriptions-item>
          <el-descriptions-item label="파일명">{{ drawerFile.originalName }}</el-descriptions-item>
          <el-descriptions-item label="크기">{{ (drawerFile.fileSize / 1024).toFixed(1) }} KB</el-descriptions-item>
          <el-descriptions-item label="MIME">{{ drawerFile.mimeType }}</el-descriptions-item>
          <el-descriptions-item label="상태">
            <el-tag :type="STATUS_MAP[drawerFile.status]?.type ?? 'info'" size="small">
              {{ STATUS_MAP[drawerFile.status]?.label ?? drawerFile.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="업로드 시각">
            {{ new Date(drawerFile.createdAt).toLocaleString('ko-KR') }}
          </el-descriptions-item>
          <el-descriptions-item label="수정 시각">
            {{ new Date(drawerFile.updatedAt).toLocaleString('ko-KR') }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 이벤트 이력 (ADMIN/AUDITOR만) -->
        <template v-if="authStore.canViewAudit">
          <div class="drawer-section-title">이벤트 이력</div>
          <div v-loading="drawerEventsLoading">
            <el-empty v-if="!drawerEventsLoading && drawerEvents.length === 0" description="이벤트 없음" :image-size="60" />
            <el-timeline v-else>
              <el-timeline-item
                v-for="ev in drawerEvents"
                :key="ev.id"
                :timestamp="new Date(ev.createdAt).toLocaleString('ko-KR')"
                placement="top"
                :type="ev.eventType === 'FAIL' ? 'danger' : ev.eventType === 'DONE' ? 'success' : 'primary'"
              >
                <div class="timeline-event">
                  <el-tag size="small" class="event-type-tag">{{ ev.eventType }}</el-tag>
                  <span class="event-payload">{{ formatEventPayload(ev.payload) }}</span>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </template>

        <!-- 작업 버튼 -->
        <div class="drawer-actions">
          <el-button
            v-if="drawerFile.status === 'DONE'"
            type="primary"
            size="small"
            @click="handleDownload(drawerFile.id, drawerFile.originalName)"
          >
            무해화 파일 다운로드
          </el-button>
          <el-button
            v-if="authStore.isAdmin"
            type="danger"
            size="small"
            style="margin: 0;"
            plain
            @click="drawerVisible = false; handleDelete(drawerFile.id, drawerFile.originalName)"
          >
            파일 삭제
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 삭제 확인 다이얼로그 -->
    <el-dialog
      v-model="deleteDialog.visible"
      :show-close="false"
      width="380px"
      align-center
      class="delete-confirm-dialog"
    >
      <div class="dc-body">
        <div class="dc-icon-wrap">
          <el-icon class="dc-big-icon"><Delete /></el-icon>
        </div>
        <p class="dc-title">{{ deleteDialog.isBulk ? '일괄 삭제' : '파일 삭제' }}</p>
        <template v-if="deleteDialog.isBulk">
          <p class="dc-desc">선택한 <strong>{{ selected.length }}개</strong> 파일을 삭제합니다.<br>이 작업은 되돌릴 수 없습니다.</p>
          <div class="dc-file-list">
            <div v-for="f in selected" :key="f.id" class="dc-file-item">
              <el-icon class="dc-file-icon"><Warning /></el-icon>
              <span class="dc-file-name">{{ f.originalName }}</span>
            </div>
          </div>
        </template>
        <template v-else>
          <p class="dc-desc">아래 파일을 삭제합니다.<br>이 작업은 되돌릴 수 없습니다.</p>
          <div class="dc-file-list">
            <div class="dc-file-item">
              <el-icon class="dc-file-icon"><Warning /></el-icon>
              <span class="dc-file-name">{{ deleteDialog.targetName }}</span>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <div class="dc-footer">
          <button class="dc-cancel-btn" :disabled="deleteDialog.deleting" @click="deleteDialog.visible = false">
            취소
          </button>
          <button class="dc-confirm-btn" :disabled="deleteDialog.deleting" @click="confirmDelete">
            <el-icon v-if="deleteDialog.deleting" class="is-loading"><Loading /></el-icon>
            <el-icon v-else><Delete /></el-icon>
            {{ deleteDialog.deleting ? '삭제 중...' : '삭제' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.list-page-container {
  height: calc(100vh - 102px);
  display: flex;
  flex-direction: column;
}
.list-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden; /* 내부 스크롤을 위해 필수 */
}
:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 20px; /* 기존 여백 유지 */
}
:deep(.el-table) {
  flex: 1;
}
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.list-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.row-hint {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--text-muted);
  background: var(--bg-muted);
  border: 1px solid var(--bg-border);
  border-radius: 20px;
  padding: 3px 10px 3px 7px;
  letter-spacing: 0.1px;
  user-select: none;
}
.row-hint-icon {
  font-size: 12px;
  color: var(--primary);
  opacity: 0.8;
}

/* 필터 바 */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.filter-left {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.filter-input {
  width: 200px;
}
.filter-select {
  width: 110px;
}

/* 일괄 작업 */
.bulk-actions {
  display: flex;
  align-items: center;
}
.bulk-count {
  font-size: 12px;
  color: var(--primary);
  font-weight: 600;
  margin: 0 8px 0 0;
}

/* 처리중 인라인 */
.processing-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.processing-bar {
  width: 110px;
}
.processing-bar :deep(.el-progress__text) {
  display: none;
}
.processing-label {
  font-size: 11px;
  color: var(--color-warning);
  font-weight: 500;
}
.action-cell {
  display: flex;
  gap: 0px;
  white-space: nowrap;
}
.action-button-danger {
  margin-left: 4px;
}
/* 클릭 가능한 행 */
:deep(.clickable-row) {
  cursor: pointer;
}
:deep(.clickable-row:hover td) {
  background: var(--primary-light) !important;
}

/* 페이지 */
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}

/* Drawer */
.drawer-desc {
  margin-bottom: 20px;
}
.drawer-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--primary);
  text-transform: uppercase;
  letter-spacing: 0.4px;
  margin: 16px 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--bg-border);
}
.timeline-event {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.event-type-tag {
  font-size: 11px !important;
}
.event-payload {
  font-size: 11px;
  color: var(--text-secondary);
}
.drawer-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--bg-border);
}
</style>

<style>
/* 삭제 확인 다이얼로그 */
.delete-confirm-dialog .el-dialog {
  border-radius: 16px !important;
  padding: 0 !important;
  overflow: hidden;
  border: 1px solid var(--bg-border);
  background: var(--bg-surface) !important;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.18) !important;
}
.delete-confirm-dialog .el-dialog__header { display: none !important; }
.delete-confirm-dialog .el-dialog__body   { padding: 32px 28px 20px !important; }
.delete-confirm-dialog .el-dialog__footer { padding: 0 28px 28px !important; }

.dc-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}
.dc-icon-wrap {
  width: 60px;
  height: 60px;
  border-radius: 16px;
  background: rgba(208, 48, 80, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
.dc-big-icon {
  font-size: 28px;
  color: var(--color-danger);
}
.dc-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.3px;
}
.dc-desc {
  font-size: 13.5px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.6;
}
.dc-desc strong {
  color: var(--color-danger);
  font-weight: 700;
}
.dc-file-list {
  width: 100%;
  max-height: 130px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 2px;
}
.dc-file-item {
  display: flex;
  align-items: center;
  gap: 7px;
  background: var(--bg-muted);
  border: 1px solid var(--bg-border);
  border-radius: 8px;
  padding: 7px 10px;
  text-align: left;
}
.dc-file-icon {
  font-size: 13px;
  color: var(--color-warning);
  flex-shrink: 0;
}
.dc-file-name {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 푸터 */
.dc-footer {
  display: flex;
  gap: 10px;
}
.dc-cancel-btn,
.dc-confirm-btn {
  flex: 1;
  height: 40px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid;
  transition: background 0.18s, border-color 0.18s, opacity 0.18s;
}
.dc-cancel-btn {
  background: var(--bg-muted);
  border-color: var(--bg-border);
  color: var(--text-secondary);
}
.dc-cancel-btn:hover:not(:disabled) {
  background: var(--bg-surface);
  border-color: var(--text-muted);
  color: var(--text-primary);
}
.dc-confirm-btn {
  background: var(--color-danger);
  border-color: var(--color-danger);
  color: #fff;
}
.dc-confirm-btn:hover:not(:disabled) {
  background: #b82040;
  border-color: #b82040;
}
.dc-cancel-btn:disabled,
.dc-confirm-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
