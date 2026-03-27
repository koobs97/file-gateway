<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled, Document, CircleCheckFilled, CircleCloseFilled, Loading, InfoFilled, Checked, CircleCheck, Warning, Filter, ArrowRight } from '@element-plus/icons-vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { fileApi } from '../api/files'
import type { FileUploadResponse, FileDetailResponse, FileStatistics, FileStatusNotification } from '../types/file'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const ALLOWED = ['docx', 'xlsx', 'pptx']

// ── 최근 처리 현황 ──────────────────────────────
const recentFiles = ref<FileDetailResponse[]>([])
const stats = ref<FileStatistics | null>(null)

async function loadRecentData() {
  try {
    const res = await fileApi.getList(0, 5)
    recentFiles.value = res.data.data?.content ?? []
    // 아직 처리중인 파일은 WebSocket 구독 (놓친 알림 방지)
    recentFiles.value
      .filter((f) => f.status === 'PROCESSING' || f.status === 'UPLOADED')
      .forEach((f) => subscribeFileStatus(f.id))
  } catch { /* ignore */ }
  if (authStore.isAdmin) {
    try {
      const res = await fileApi.getStatistics()
      stats.value = res.data.data ?? null
    } catch { /* ignore */ }
  }
}

// ── WebSocket — 업로드 파일 상태 실시간 반영 ──
let stompClient: Client | null = null
const subscribedIds = new Set<number>()

function ensureWsConnected(): Promise<void> {
  return new Promise((resolve) => {
    if (stompClient?.connected) { resolve(); return }
    stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => resolve(),
    })
    stompClient.activate()
  })
}

async function subscribeFileStatus(fileId: number) {
  if (subscribedIds.has(fileId)) return
  subscribedIds.add(fileId)
  await ensureWsConnected()
  stompClient?.subscribe(`/topic/files/${fileId}`, (msg) => {
    const n: FileStatusNotification = JSON.parse(msg.body)
    // 목록에서 해당 파일 상태만 즉시 업데이트
    const idx = recentFiles.value.findIndex((f) => f.id === n.fileId)
    if (idx !== -1) {
      recentFiles.value[idx] = { ...recentFiles.value[idx], status: n.status }
    }
    // 처리 완료 시 통계도 갱신
    if (n.status === 'DONE' || n.status === 'FAIL') {
      if (authStore.isAdmin) {
        fileApi.getStatistics().then((r) => { stats.value = r.data.data ?? null }).catch(() => {})
      }
    }
  })
}

onMounted(loadRecentData)
onUnmounted(() => { stompClient?.deactivate() })

function timeAgo(dateStr: string): string {
  const m = Math.floor((Date.now() - new Date(dateStr).getTime()) / 60000)
  if (m < 1) return '방금 전'
  if (m < 60) return `${m}분 전`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}시간 전`
  return `${Math.floor(h / 24)}일 전`
}

function statusTagType(status: string) {
  return ({ DONE: 'success', PROCESSING: 'warning', FAIL: 'danger', UPLOADED: 'info' } as Record<string, string>)[status] ?? 'info'
}
function statusTagLabel(status: string) {
  return ({ DONE: '완료', PROCESSING: '처리중', FAIL: '실패', UPLOADED: '대기중' } as Record<string, string>)[status] ?? status
}

// ── 업로드 큐 ──────────────────────────────────
interface QueueItem {
  uid: string
  file: File
  status: 'waiting' | 'uploading' | 'done' | 'error'
  progress: number
  result?: FileUploadResponse
  errorMsg?: string
}

const queue = ref<QueueItem[]>([])
const isProcessing = ref(false)

function getExt(name: string) {
  return name.split('.').pop()?.toLowerCase() ?? ''
}

function onFilesSelected(uploadFile: any) {
  const file: File = uploadFile.raw
  if (!ALLOWED.includes(getExt(file.name))) {
    ElMessage.error(`허용되지 않는 파일 형식입니다. (허용: ${ALLOWED.join(', ')})`)
    return false
  }
  // 중복 체크
  if (queue.value.some((q) => q.file.name === file.name && q.file.size === file.size)) {
    ElMessage.warning('이미 큐에 추가된 파일입니다.')
    return false
  }
  queue.value.push({
    uid: Math.random().toString(36).slice(2),
    file,
    status: 'waiting',
    progress: 0,
  })
  if (!isProcessing.value) processQueue()
  return false
}

async function processQueue() {
  isProcessing.value = true
  for (const item of queue.value) {
    if (item.status !== 'waiting') continue
    item.status = 'uploading'
    try {
      const res = await fileApi.upload(item.file, (p) => { item.progress = p })
      item.result = res.data.data
      item.status = 'done'
    } catch {
      item.status = 'error'
      item.errorMsg = '업로드 실패'
    }
  }
  isProcessing.value = false
}

function removeItem(uid: string) {
  queue.value = queue.value.filter((q) => q.uid !== uid)
}

function clearDone() {
  queue.value = queue.value.filter((q) => q.status !== 'done')
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const doneCount = () => queue.value.filter((q) => q.status === 'done').length

// 업로드 완료 시: WebSocket 구독 완료 확인 후 목록 갱신
watch(
  () => queue.value.filter((q) => q.status === 'done'),
  async (doneItems, prevDoneItems) => {
    const newlyDone = doneItems.filter(
      (item) => !prevDoneItems.some((p) => p.uid === item.uid)
    )
    if (newlyDone.length === 0) return
    // 구독이 모두 활성화된 뒤 목록 조회 → CDR 완료 알림을 놓치지 않음
    await Promise.all(
      newlyDone
        .filter((item) => item.result?.fileId)
        .map((item) => subscribeFileStatus(item.result!.fileId))
    )
    loadRecentData()
  },
  { deep: true }
)
</script>

<template>
  <el-row :gutter="20" class="upload-row">
    <!-- 왼쪽: 업로드 영역 + 큐 -->
    <el-col :span="16" class="left-col">
      <el-card>
        <template #header>
          <div class="card-header-row">
            <span>파일 업로드</span>
            <div style="display:flex; gap:6px;">
              <button
                v-if="doneCount() > 0"
                type="button"
                class="hdr-btn"
                @click="clearDone"
              >
                완료 항목 지우기
              </button>
              <button
                v-if="queue.length > 0"
                type="button"
                class="link-btn"
                @click="router.push('/files')"
              >
                처리 목록으로
              </button>
            </div>
          </div>
        </template>

        <!-- 드래그 업로드 -->
        <el-upload
          drag
          multiple
          :auto-upload="false"
          :show-file-list="false"
          :on-change="onFilesSelected"
          accept=".docx,.xlsx,.pptx"
          class="upload-dragger"
        >
          <el-icon :size="36" class="upload-icon"><UploadFilled /></el-icon>
          <div class="upload-text">파일을 드래그하거나 클릭하여 업로드</div>
          <div class="upload-hint">지원 형식: .docx, .xlsx, .pptx &nbsp;/&nbsp; 최대 100MB &nbsp;/&nbsp; 다중 선택 가능</div>
        </el-upload>

        <!-- 파일 큐 -->
        <div class="queue-section">
          <div class="queue-header">
            <span class="queue-title">업로드 대기열 ({{ queue.length }}건)</span>
          </div>
          <div class="queue-list">
            <div v-for="item in queue" :key="item.uid" class="queue-item">
              <el-icon class="queue-file-icon"><Document /></el-icon>
              <div class="queue-info">
                <div class="queue-name" :title="item.file.name">{{ item.file.name }}</div>
                <div class="queue-meta">{{ formatSize(item.file.size) }}</div>
                <el-progress
                  v-if="item.status === 'uploading'"
                  :percentage="item.progress"
                  :stroke-width="4"
                  class="queue-progress"
                />
              </div>
              <div class="queue-status">
                <el-tag v-if="item.status === 'waiting'" size="small" type="info">대기</el-tag>
                <el-tag v-else-if="item.status === 'uploading'" size="small" type="warning">
                  <el-icon class="is-loading"><Loading /></el-icon> 업로드중
                </el-tag>
                <el-tag v-else-if="item.status === 'done'" size="small" type="success">
                  <el-icon><CircleCheckFilled /></el-icon> 완료
                </el-tag>
                <el-tag v-else-if="item.status === 'error'" size="small" type="danger">
                  <el-icon><CircleCloseFilled /></el-icon> 실패
                </el-tag>
              </div>
              <el-button
                v-if="item.status !== 'uploading'"
                size="small"
                text
                type="danger"
                class="queue-remove"
                @click="removeItem(item.uid)"
              >
                제거
              </el-button>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 최근 처리 현황 카드 -->
      <el-card class="recent-card">
        <template #header>
          <div class="card-header-row">
            <span>최근 처리 현황</span>
            <button type="button" class="link-btn" @click="router.push('/files')">
              전체 목록 <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </template>

        <!-- 통계 칩 (admin 한정) -->
        <div v-if="stats" class="stat-row">
          <div class="stat-chip chip-total">
            <span class="stat-num">{{ stats.total }}</span>
            <span class="stat-lbl">전체</span>
          </div>
          <div class="stat-chip chip-done">
            <span class="stat-num">{{ stats.done }}</span>
            <span class="stat-lbl">완료</span>
          </div>
          <div class="stat-chip chip-proc">
            <span class="stat-num">{{ stats.processing }}</span>
            <span class="stat-lbl">처리중</span>
          </div>
          <div class="stat-chip chip-fail">
            <span class="stat-num">{{ stats.fail }}</span>
            <span class="stat-lbl">실패</span>
          </div>
          <div class="stat-chip chip-rate">
            <span class="stat-num">{{ stats.successRate }}%</span>
            <span class="stat-lbl">성공률</span>
          </div>
        </div>

        <!-- 최근 파일 리스트 -->
        <div class="recent-list slim-scroll">
          <div v-for="f in recentFiles" :key="f.id" class="recent-item">
            <el-icon class="recent-doc-icon"><Document /></el-icon>
            <div class="recent-info">
              <span class="recent-name" :title="f.originalName">{{ f.originalName }}</span>
              <span class="recent-time">{{ timeAgo(f.createdAt) }}</span>
            </div>
            <el-tag :type="(statusTagType(f.status) as any)" size="small" effect="light">
              {{ statusTagLabel(f.status) }}
            </el-tag>
          </div>
          <div v-if="recentFiles.length === 0" class="empty-placeholder">
            최근 처리 이력이 없습니다.
          </div>
        </div>
      </el-card>
    </el-col>

    <!-- 오른쪽: 가이드 패널 -->
    <el-col :span="8" class="right-col">
      <div class="guide-container">
        <!-- 메인 타이틀 (카드 밖 혹은 상단) -->
        <div class="guide-main-header">
          <el-icon><InfoFilled /></el-icon>
          <span>안전한 파일 처리를 위한 가이드</span>
        </div>

        <!-- 1. 지원 형식 & 제한 사항 (Compact Card) -->
        <div class="guide-card-modern">
          <div class="g-section-head">
            <div class="g-icon-box primary"><Document /></div>
            <div class="g-title-area">
              <span class="g-title">파일 규격</span>
              <span class="g-subtitle">Supported Formats & Size</span>
            </div>
          </div>
          
          <div class="format-grid">
            <div class="format-item"><span>DOCX</span></div>
            <div class="format-item"><span>XLSX</span></div>
            <div class="format-item"><span>PPTX</span></div>
          </div>
          
          <div class="limit-info">
            <div class="limit-row">
              <el-icon><CircleCheck /></el-icon>
              <span>최대 <strong>100MB</strong>까지 업로드 가능</span>
            </div>
            <div class="limit-row">
              <el-icon><CircleCheck /></el-icon>
              <span>Office 2007 이상 공식 규격 지원</span>
            </div>
          </div>
        </div>

        <!-- 2. CDR 무해화 핵심 (Security Card) -->
        <div class="guide-card-modern security">
          <div class="g-section-head">
            <div class="g-icon-box danger"><Filter /></div>
            <div class="g-title-area">
              <span class="g-title">무해화 공정 (CDR)</span>
              <span class="g-subtitle">Security Analysis</span>
            </div>
          </div>
          
          <div class="security-list">
            <div class="security-item">
              <span class="dot"></span>
              <span class="label">VBA 매크로 / 스크립트 제거</span>
            </div>
            <div class="security-item">
              <span class="dot"></span>
              <span class="label">외부 연결 및 원격 참조 차단</span>
            </div>
            <div class="security-item">
              <span class="dot"></span>
              <span class="label">ActiveX 및 OLE 객체 비활성</span>
            </div>
            <div class="security-item">
              <span class="dot"></span>
              <span class="label">고위험 메타데이터 파기</span>
            </div>
          </div>
          <div class="security-badge">
            <el-icon><Warning /></el-icon> 분석된 위험 요소는 영구 삭제됩니다.
          </div>
        </div>

        <!-- 3. 처리 단계 (Process Card) -->
        <div class="guide-card-modern">
          <div class="g-section-head">
            <div class="g-icon-box warning"><Checked /></div>
            <div class="g-title-area">
              <span class="g-title">처리 단계</span>
              <span class="g-subtitle">Process Workflow</span>
            </div>
          </div>
          
          <div class="workflow-steps">
            <div class="step-item">
              <div class="step-num">01</div>
              <div class="step-content">
                <div class="step-label">파일 수신</div>
                <div class="step-desc">서버 전송 및 무결성 검사</div>
              </div>
            </div>
            <div class="step-line"></div>
            <div class="step-item">
              <div class="step-num">02</div>
              <div class="step-content">
                <div class="step-label">무해화(CDR)</div>
                <div class="step-desc">액티브 콘텐츠 추출 및 제거</div>
              </div>
            </div>
            <div class="step-line"></div>
            <div class="step-item">
              <div class="step-num">03</div>
              <div class="step-content">
                <div class="step-label">재구성/다운로드</div>
                <div class="step-desc">안전한 파일로 재조합 완료</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-col>
  </el-row>
</template>

<style scoped>
/* ─── 업로드 드래거 ──────────────────────────── */
:deep(.upload-dragger) {
  width: 100%;
}
:deep(.el-upload-dragger) {
  width: 100%;
  height: 150px; /* 적정 높이로 조절 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border-radius: 12px;
  border: 1px dashed var(--el-border-color-darker); /* 선을 얇게 */
  background-color: var(--el-fill-color-blank); /* 기본은 깔끔한 흰색/다크색 */
  transition: all 0.25s ease;
}

:deep(.el-upload-dragger:hover),
:deep(.el-upload-dragger.is-dragover) {
  border-color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9); /* 아주 연한 강조색 */
  border-style: solid; /* 호버 시 실선으로 변경하여 명확한 피드백 */
}

.upload-icon {
  color: var(--el-color-primary);
  font-size: 40px !important; /* 너무 크지 않게 조절 */
  opacity: 0.7;
}

.upload-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  letter-spacing: -0.3px;
}

.upload-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  opacity: 0.8;
}

/* ─── 업로드 대기열 (슬림 스크롤 & 콤팩트 디자인) ─── */
.queue-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.queue-header {
  margin-bottom: 10px;
}

.queue-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
  display: block;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 197px;
  max-height: 197px;
  overflow-y: auto;
  scrollbar-gutter: stable;
  padding-right: 4px;
}

/* 슬림한 스크롤바 디자인 */
.queue-list::-webkit-scrollbar {
  width: 4px;
}
.queue-list::-webkit-scrollbar-thumb {
  background: var(--el-border-color-lighter);
  border-radius: 10px;
}
.queue-list:hover::-webkit-scrollbar-thumb {
  background: var(--el-text-color-placeholder);
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--bg-muted);
  border: 1px solid var(--bg-border);
  border-radius: 10px;
  transition: background 0.2s, border-color 0.2s;
}

.queue-item:hover {
  background: var(--bg-surface);
  border-color: var(--primary);
}

.queue-file-icon {
  font-size: 18px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.queue-info {
  flex: 1;
  min-width: 0;
}

.queue-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.queue-meta {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 1px;
}
/* 제거 버튼 스타일 */
.queue-remove {
  margin-left: auto;
  padding: 4px 8px !important;
  font-size: 11px !important;
  font-weight: 500;
  color: var(--text-secondary) !important;
  transition: all 0.2s ease;
}
.queue-remove:hover {
  color: var(--color-danger) !important;
  background-color: rgba(208, 48, 80, 0.08) !important;
}

/* 업로드 중일 때는 버튼이 없으므로, 
   아이템 내의 상태 태그(.queue-status)가 오른쪽 끝에 오도록 유도 */
.queue-status {
  margin-left: auto; 
  display: flex;
  align-items: center;
}

/* 만약 제거 버튼이 있을 때는 상태 태그의 margin-left를 해제 */
.queue-item:has(.queue-remove) .queue-status {
  margin-left: 0;
}

/* ─── 레이아웃 및 카드 밸런스 ─── */
.recent-card {
  margin-top: 0px;
  max-height: 300px;
}

:deep(.upload-row) {
  align-items: stretch;
}

.left-col {
  display: flex;
  flex-direction: column;
}

/* 가이드 카드 섹션 타이틀 강조만 살짝 */
.g-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  font-family: 'Outfit', sans-serif;
}

/* 다크모드: 업로드 드래거 배경 */
html.dark :deep(.el-upload-dragger) {
  background-color: var(--bg-muted);
  border-color: var(--bg-border);
}

/* ─── 레이아웃 stretch ───────────────────────── */
:deep(.upload-row) {
  align-items: stretch;
}
.left-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
/* recent-card가 남은 공간 채우도록 — el-card 래퍼 포함 */
.left-col > :last-child {
  flex: 1;
}
.right-col {
  display: flex;
  flex-direction: column;
}

/* 가이드 컨테이너 */
.guide-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
/* 마지막 가이드 카드가 남은 공간 채움 */
.guide-card-modern:last-child {
  flex: 1;
}

.guide-main-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 4px;
  font-weight: 700;
  color: var(--text-primary);
  font-size: 15px;
}

/* 모던 카드 공통 스타일 */
.guide-card-modern {
  background: var(--bg-surface);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid var(--bg-border);
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);
}

.guide-card-modern:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

/* 섹션 헤더 */
.g-section-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.g-icon-box {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}
.g-icon-box.primary { background: #eef2ff; color: #4f46e5; width: 32px; height: 32px; padding: 6px; }
.g-icon-box.danger  { background: #fff1f2; color: #e11d48; width: 32px; height: 32px; padding: 6px; }
.g-icon-box.warning { background: #fffbeb; color: #d97706; width: 32px; height: 32px; padding: 6px; }

.g-title-area {
  display: flex;
  flex-direction: column;
}

.g-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
}

.g-subtitle {
  font-size: 10px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 1. 파일 형식 그리드 */
.format-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.format-item {
  background: var(--bg-muted);
  border: 1px solid var(--bg-border);
  border-radius: 6px;
  padding: 6px;
  text-align: center;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
}

.limit-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.limit-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--text-secondary);
}
.limit-row .el-icon { color: var(--color-success); }

/* 2. 보안 리스트 (무해화) */
.guide-card-modern.security {
  background: var(--bg-surface);
  border: 1px solid rgba(225, 29, 72, 0.3);
}

.security-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.security-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: rgba(225, 29, 72, 0.05);
  border-radius: 6px;
}

.security-item .dot {
  width: 4px;
  height: 4px;
  background: #e11d48;
  border-radius: 50%;
  flex-shrink: 0;
}

.security-item .label {
  font-size: 11px;
  color: var(--color-danger);
  font-weight: 500;
}

.security-badge {
  margin-top: 12px;
  font-size: 10px;
  color: var(--color-danger);
  background: rgba(225, 29, 72, 0.08);
  padding: 6px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 3. 워크플로우 (단계) */
.workflow-steps {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.step-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.step-num {
  width: 22px;
  height: 22px;
  background: var(--bg-muted);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: var(--text-muted);
  flex-shrink: 0;
}

.step-item.active .step-num {
  background: var(--primary);
  color: var(--text-inverse);
}

.step-content {
  margin-top: 2px;
}

.step-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.step-desc {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.step-line {
  width: 1px;
  height: 15px;
  background: var(--bg-border);
  margin-left: 11px;
}

/* 다크모드: 아이콘 박스 배경 조정 */
:global(html.dark) .g-icon-box.primary { background: rgba(79, 70, 229, 0.2); }
:global(html.dark) .g-icon-box.danger  { background: rgba(225, 29, 72, 0.2); }
:global(html.dark) .g-icon-box.warning { background: rgba(217, 119, 6, 0.2); }

/* 카드 헤더 공통 */
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  max-height: 20px;
}
/* 공통 버튼 베이스 */
.hdr-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 7px 12px;
  font-size: 12px;
  font-weight: 600;
  font-family: 'Noto Sans KR', sans-serif;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid var(--el-border-color);
  background-color: var(--el-fill-color-blank);
  color: var(--el-text-color-regular);
  line-height: 1;
  outline: none;
}

/* 1. 완료 항목 지우기 (기본/고스트 스타일) */
.hdr-btn:hover {
  background-color: var(--el-fill-color-light);
  border-color: var(--el-color-info-light-3);
  color: var(--el-text-color-primary);
}

.hdr-btn:active {
  background-color: var(--el-fill-color-dark);
  transform: translateY(1px);
}

/* 2. 처리 목록으로 (Primary 강조 스타일) */
.hdr-btn--primary {
  background-color: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: var(--black-white-color); /* 라이트: 화이트 / 다크: 블랙 */
}

.hdr-btn--primary:hover {
  background-color: var(--el-color-primary-light-3);
  border-color: var(--el-color-primary-light-3);
  color: var(--black-white-color);
  box-shadow: var(--el-box-shadow-lighter);
  transform: translateY(-1px);
}

.hdr-btn--primary:active {
  background-color: var(--el-color-primary-dark-2);
  border-color: var(--el-color-primary-dark-2);
  transform: translateY(1px);
  box-shadow: none;
}

/* 버튼 내 아이콘 여백 (필요 시) */
.hdr-btn .el-icon {
  margin-right: 4px;
  font-size: 14px;
}

/* 다크모드 미세 조정 */
html.dark .hdr-btn {
  background-color: var(--el-fill-color-darker);
  border-color: var(--el-border-color-darker);
}

html.dark .hdr-btn:hover {
  background-color: var(--el-fill-color-dark);
  border-color: var(--el-border-color-dark);
}
/* 전체 목록 링크 버튼 — light/dark 모두 명확 */
.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: var(--primary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 5px;
  transition: background 0.15s, color 0.15s;
  font-family: inherit;
}
.link-btn:hover {
  background: var(--primary-light);
  color: var(--primary-hover);
}

/* ─── 최근 처리 현황 카드 ───────────────────── */
.recent-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-height: 270px;
}
:deep(.recent-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 통계 칩 행 */
.stat-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.stat-chip {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px 6px;
  border-radius: 8px;
  border: 1px solid var(--bg-border);
}

.stat-num {
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
}

.stat-lbl {
  font-size: 10px;
  color: var(--text-muted);
  font-weight: 500;
}

.empty-placeholder {
  height: 100%; display: flex; align-items: center; justify-content: center;
  font-size: 13px; color: var(--el-text-color-placeholder);
}

.slim-scroll::-webkit-scrollbar { width: 4px; }
.slim-scroll::-webkit-scrollbar-thumb { background: var(--el-border-color-lighter); border-radius: 10px; }
.slim-scroll:hover::-webkit-scrollbar-thumb { background: var(--el-text-color-placeholder); }

.chip-total  { background: var(--bg-muted); }
.chip-total .stat-num  { color: var(--text-primary); }

.chip-done   { background: rgba(24, 160, 88, 0.08); border-color: rgba(24, 160, 88, 0.2); }
.chip-done .stat-num   { color: var(--color-success); }

.chip-proc   { background: rgba(240, 160, 32, 0.08); border-color: rgba(240, 160, 32, 0.2); }
.chip-proc .stat-num   { color: var(--color-warning); }

.chip-fail   { background: rgba(208, 48, 80, 0.08); border-color: rgba(208, 48, 80, 0.2); }
.chip-fail .stat-num   { color: var(--color-danger); }

.chip-rate   { background: rgba(58, 95, 160, 0.08); border-color: rgba(58, 95, 160, 0.2); }
.chip-rate .stat-num   { color: var(--color-info); }

/* 최근 파일 리스트 */
.recent-list {
  height: 160px; /* 3개 항목에 최적화된 높이 */
  overflow-y: auto;
  margin-top: 12px;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 7px;
  transition: background 0.15s;
}
.recent-item:hover {
  background: var(--bg-muted);
}

.recent-doc-icon {
  color: var(--text-muted);
  font-size: 16px;
  flex-shrink: 0;
}

.recent-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.recent-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-time {
  font-size: 11px;
  color: var(--text-muted);
}

.recent-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 24px 0;
  color: var(--text-muted);
  font-size: 13px;
}
</style>
