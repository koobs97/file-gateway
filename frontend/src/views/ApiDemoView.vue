<!--
  ApiDemoView
  - CDR API 외부 연동 데모 클라이언트
  - JWT 없이 X-API-Key 인증만으로 CDR 서비스를 체험하는 4단계 시뮬레이터
  - 완전히 독립된 인터페이스로 외부 시스템 관점의 API 연동을 시연한다

  @props 없음
  @emits 없음
-->
<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Key, Upload, Loading, CircleCheck, Download, Connection } from '@element-plus/icons-vue'

const router = useRouter()

// ─── 단계 정의 ───────────────────────────────────────────────────────────────
const STEPS = [
  { num: 1, label: 'API 인증' },
  { num: 2, label: '파일 선택' },
  { num: 3, label: 'CDR 처리' },
  { num: 4, label: '결과 확인' },
]

/** 현재 활성 단계 번호 */
const currentStep = ref(1)

// ─── Step 1: API 인증 ────────────────────────────────────────────────────────
/** 입력된 X-API-Key */
const apiKey = ref('')
/** 연결 테스트 진행 중 여부 */
const connecting = ref(false)
/** 연결 성공 여부 */
const connected = ref(false)

/** X-API-Key 인증 전용 axios 인스턴스를 생성한다 (JWT 미사용) */
function createClient() {
  return axios.create({
    baseURL: '/api/v1',
    timeout: 30000,
    headers: { 'X-API-Key': apiKey.value },
  })
}

/**
 * API 키 연결 테스트 — 파일 목록 1건 조회로 인증을 검증한다.
 * 성공 시 Step 2로 자동 이동한다.
 */
async function testConnection() {
  if (!apiKey.value.trim()) {
    ElMessage.warning('API 키를 입력해 주세요.')
    return
  }
  connecting.value = true
  try {
    await createClient().get('/files?page=0&size=1')
    connected.value = true
    ElMessage.success('연결 성공! 파일 선택 단계로 이동합니다.')
    setTimeout(() => { currentStep.value = 2 }, 900)
  } catch {
    ElMessage.error('유효하지 않은 API 키입니다. 관리자 콘솔에서 발급된 키를 입력하세요.')
  } finally {
    connecting.value = false
  }
}

// ─── Step 2: 파일 선택 ───────────────────────────────────────────────────────
const ALLOWED_EXTS = ['.docx', '.xlsx', '.pptx']

/** 선택된 업로드 파일 */
const selectedFile = ref<File | null>(null)
/** 드래그 오버 활성 상태 */
const isDragging = ref(false)
/** 업로드 요청 진행 중 여부 */
const uploading = ref(false)
/** 숨김 file input DOM 참조 */
const fileInputRef = ref<HTMLInputElement | null>(null)

/** 드래그 드롭 파일 처리 */
function onDrop(e: DragEvent) {
  isDragging.value = false
  const file = e.dataTransfer?.files[0]
  if (file) selectFile(file)
}

/** 파일 선택 input 변경 처리 */
function onFileInput(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) selectFile(file)
}

/** 파일 유효성 검사 후 상태에 등록한다 */
function selectFile(file: File) {
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (!ALLOWED_EXTS.includes(ext)) {
    ElMessage.error('DOCX, XLSX, PPTX 파일만 지원합니다.')
    return
  }
  selectedFile.value = file
}

/** 바이트 수를 사람이 읽기 좋은 크기 문자열로 변환한다 */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** 확장자에 따른 이모지 반환 */
function getFileEmoji(name: string): string {
  const ext = name.split('.').pop()?.toLowerCase()
  if (ext === 'docx') return '📄'
  if (ext === 'xlsx') return '📊'
  if (ext === 'pptx') return '📑'
  return '📁'
}

/**
 * CDR 처리를 시작한다 — 파일을 업로드하고 Step 3으로 이동하여 폴링을 시작한다.
 */
async function startCdr() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', selectedFile.value)
    const res = await createClient().post('/files', form)
    fileId.value = res.data.data.fileId || res.data.data.id 
    uploadTime.value = new Date()
    fileStatus.value = 'UPLOADED'
    currentStep.value = 3
    startPolling()
  } catch {
    ElMessage.error('파일 업로드에 실패했습니다.')
  } finally {
    uploading.value = false
  }
}

// ─── Step 3: CDR 처리 ────────────────────────────────────────────────────────
/** 업로드된 파일 ID */
const fileId = ref<number | null>(null)
/** 현재 파일 처리 상태 (UPLOADED / PROCESSING / DONE / FAIL) */
const fileStatus = ref('')
/** 처리 이벤트 목록 (실시간 갱신) */
const events = ref<any[]>([])
/** 폴링 인터벌 타이머 ID */
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)
/** 업로드 완료 시각 */
const uploadTime = ref<Date | null>(null)
/** CDR 처리 완료 시각 */
const completedAt = ref<Date | null>(null)
/** 폴링 실행 횟수 (디버그 표시용) */
const pollCount = ref(0)
/** 폴링 중 발생한 오류 메시지 */
const pollErrMsg = ref('')

/**
 * 파일 상태와 이벤트를 2초 주기로 폴링하여 실시간 업데이트한다.
 * 즉시 첫 번째 폴을 실행하고 이후 2초 간격으로 반복한다.
 * 상태 조회와 이벤트 조회를 독립적으로 처리하여
 * 이벤트 조회 실패(권한 오류 등)가 상태 갱신을 막지 않도록 한다.
 * DONE 또는 FAIL 상태가 되면 폴링을 중단하고 Step 4로 이동한다.
 */
function startPolling() {
  const capturedId = fileId.value
  if (!capturedId) return

  // 인터벌 외부에서 클라이언트 1회 생성
  const apiClient = createClient()

  const tick = async () => {
    pollCount.value++
    
    try {
      // 1. 파일 상세 상태 조회 (우선 순위 높음)
      const statusRes = await apiClient.get(`/files/${capturedId}`)
      const latestDetail = statusRes.data.data
      fileStatus.value = latestDetail.status // 'PROCESSING', 'DONE' 등으로 업데이트
      pollErrMsg.value = '' // 에러 초기화

      // 2. 이벤트 스트림 조회 (참고용, 실패해도 중단하지 않음)
      try {
        const eventsRes = await apiClient.get(`/files/${capturedId}/events`)
        events.value = eventsRes.data.data ?? []
      } catch (e: any) {
        // 이벤트 조회가 실패하더라도 fileStatus가 정상이면 계속 진행
        console.warn('[Demo] 이벤트 조회 권한이 없거나 실패함:', e.response?.status)
      }

      // 3. 종료 조건 (DONE 또는 FAIL)
      if (['DONE', 'FAIL'].includes(fileStatus.value)) {
        if (pollTimer.value) clearInterval(pollTimer.value)
        pollTimer.value = null
        completedAt.value = new Date()
        fileDetail.value = latestDetail
        
        // 약간의 딜레이를 주어 사용자가 완료 상태를 인지하게 한 뒤 결과 창으로 이동
        setTimeout(() => { currentStep.value = 4 }, 1000)
      }
    } catch (e: any) {
      // API Key가 유효하지 않거나 서버 에러 시
      const code = e?.response?.status ?? 'ERR'
      pollErrMsg.value = `API 호출 오류 (HTTP ${code})`
      console.error('[Demo] Polling Error:', e)
    }
  }

  // 즉시 실행 후 인터벌 설정
  tick()
  pollTimer.value = setInterval(tick, 2000)
}

/**
 * 현재 이벤트 목록을 기반으로 CDR 파이프라인 각 단계의 활성화 상태를 계산한다.
 * QUEUED → 분석 단계, START → 무해화 단계, DONE → 재구성 단계에 매핑된다.
 */
const pipelineState = computed(() => {
  const types = events.value.map((e) => e.eventType)
  const status = fileStatus.value
  const isProcessing = status === 'PROCESSING'
  const isDone = status === 'DONE'

  return {
    // 1단계(분석): 상태가 PROCESSING/DONE이거나 QUEUED/START 이벤트가 있을 때
    analyze: isProcessing || isDone || types.includes('QUEUED') || types.includes('START'),
    // 2단계(무해화): 상태가 PROCESSING(일부)/DONE이거나 START 이벤트가 있을 때
    sanitize: isDone || (isProcessing && types.includes('START')) || types.includes('START'),
    // 3단계(재구성): 상태가 DONE이거나 DONE 이벤트가 있을 때
    reconstruct: isDone || types.includes('DONE'),
  }
})

// ─── Step 4: 결과 ─────────────────────────────────────────────────────────────
/** 최종 파일 상세 정보 */
const fileDetail = ref<any | null>(null)

/** 업로드부터 처리 완료까지 소요 시간 (초) */
const processingTime = computed(() => {
  if (!uploadTime.value || !completedAt.value) return null
  return ((completedAt.value.getTime() - uploadTime.value.getTime()) / 1000).toFixed(1)
})

/**
 * 무해화된 파일을 다운로드한다.
 */
async function downloadFile() {
  if (!fileId.value || !selectedFile.value) return
  try {
    const res = await createClient().get(`/files/${fileId.value}/download`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `sanitized_${selectedFile.value.name}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('무해화 파일 다운로드 완료')
  } catch {
    ElMessage.error('다운로드에 실패했습니다.')
  }
}

// ─── 공통 유틸 ───────────────────────────────────────────────────────────────
/**
 * ISO 날짜 문자열을 HH:MM:SS 형식의 시각 문자열로 변환한다.
 *
 * @param dt ISO 날짜 문자열
 * @returns 로컬 시각 문자열
 */
function formatEventTime(dt: string): string {
  if (!dt) return ''
  return new Date(dt).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

/**
 * 이벤트 payload(JSON 문자열 또는 null)를 간결한 한 줄 텍스트로 변환한다.
 *
 * @param payload JSON 문자열 또는 null
 * @returns 'key: value · ...' 형식의 요약 문자열
 */
function formatEventPayload(payload: string | null): string {
  if (!payload) return ''
  try {
    const obj = JSON.parse(payload)
    return Object.entries(obj)
      .map(([k, v]) => `${k}: ${v}`)
      .join(' · ')
  } catch {
    return payload
  }
}

/**
 * EventType 코드를 한국어 레이블로 변환한다.
 *
 * @param type EventType 코드 (QUEUED / START / DONE / FAIL)
 * @returns 한국어 레이블
 */
function getEventLabel(type: string): string {
  const map: Record<string, string> = {
    QUEUED: '대기열 등록',
    START: 'CDR 시작',
    DONE: '처리 완료',
    FAIL: '처리 실패',
  }
  return map[type] ?? type
}

/** 데모를 초기 상태로 완전히 리셋한다 */
function resetDemo() {
  if (pollTimer.value) clearInterval(pollTimer.value)
  currentStep.value = 1
  apiKey.value = ''
  connected.value = false
  selectedFile.value = null
  fileId.value = null
  fileStatus.value = ''
  events.value = []
  fileDetail.value = null
  uploadTime.value = null
  completedAt.value = null
}

onUnmounted(() => {
  if (pollTimer.value) clearInterval(pollTimer.value)
})
</script>

<template>
  <div class="demo-root">

    <!-- ══════════════ 헤더 ══════════════ -->
    <header class="demo-header">
      <div class="demo-header-left">
        <span class="env-badge">⚡ EXTERNAL CLIENT SIMULATOR</span>
        <h1 class="demo-title">CDR API 연동 데모</h1>
        <p class="demo-sub">X-API-Key 인증 기반 · 외부 시스템 관점의 CDR 서비스 연동 시뮬레이터</p>
      </div>
      <button class="back-btn" @click="router.push('/admin')">
        ← 관리 콘솔로
      </button>
    </header>

    <!-- ══════════════ 스텝 인디케이터 ══════════════ -->
    <nav class="stepper">
      <template v-for="(step, idx) in STEPS" :key="step.num">
        <div
          class="sn"
          :class="{ active: currentStep === step.num, done: currentStep > step.num }"
        >
          <div class="sn-circle">
            <el-icon v-if="currentStep > step.num"><CircleCheck /></el-icon>
            <span v-else>{{ step.num }}</span>
          </div>
          <span class="sn-label">{{ step.label }}</span>
        </div>
        <div v-if="idx < STEPS.length - 1" class="sn-line" :class="{ done: currentStep > step.num }" />
      </template>
    </nav>

    <!-- ══════════════ 메인 콘텐츠 ══════════════ -->
    <main class="demo-main">
      <transition name="slide-fade" mode="out-in">

        <!-- ─────── Step 1: API 인증 ─────── -->
        <div v-if="currentStep === 1" key="s1" class="card">
          <div class="card-head">
            <div class="card-icon indigo"><el-icon><Key /></el-icon></div>
            <div>
              <div class="card-title">API 키 인증</div>
              <div class="card-desc">관리자 콘솔에서 발급한 X-API-Key로 CDR 서비스에 접근합니다</div>
            </div>
          </div>

          <div class="info-box">
            <div class="info-box-title">🔐 X-API-Key 인증 방식</div>
            <ul>
              <li>JWT 로그인 없이 <b>API 키 하나</b>만으로 CDR 서비스를 호출합니다</li>
              <li>ERP, 메일 게이트웨이, DMS 등 <b>외부 시스템</b>이 프로그래매틱하게 연동할 때 사용합니다</li>
              <li>모든 요청 헤더에 <code>X-API-Key: {key}</code> 를 포함하여 인증합니다</li>
            </ul>
          </div>

          <div class="key-row">
            <input
              v-model="apiKey"
              class="key-input"
              placeholder="관리자 콘솔에서 발급받은 API 키를 입력하세요"
              spellcheck="false"
              @keyup.enter="testConnection"
            />
            <button
              class="btn-connect"
              :class="{ success: connected }"
              :disabled="connecting"
              @click="testConnection"
            >
              <el-icon v-if="connecting" class="is-loading"><Loading /></el-icon>
              <el-icon v-else-if="connected"><CircleCheck /></el-icon>
              <el-icon v-else><Connection /></el-icon>
              {{ connecting ? '연결 중...' : connected ? '연결됨' : '연결 테스트' }}
            </button>
          </div>

          <div class="hint">
            💡 API 키는 메인 앱의 <b>시스템 관리 → API 클라이언트</b> 탭에서 생성·복사할 수 있습니다
          </div>
        </div>

        <!-- ─────── Step 2: 파일 선택 ─────── -->
        <div v-else-if="currentStep === 2" key="s2" class="card">
          <div class="card-head">
            <div class="card-icon blue"><el-icon><Upload /></el-icon></div>
            <div>
              <div class="card-title">파일 선택</div>
              <div class="card-desc">CDR 무해화를 요청할 MS Office 파일을 선택합니다</div>
            </div>
          </div>

          <div class="info-box">
            <div class="info-box-title">📤 CDR 처리 대상</div>
            <ul>
              <li><b>매크로(VBA)</b>, 외부 링크, 임베디드 OLE 등 위험 요소가 자동으로 제거됩니다</li>
              <li>원본 파일은 변경되지 않으며 <b>무해화된 새 파일</b>이 생성됩니다</li>
              <li>모든 처리 결과는 감사 이력으로 기록되어 ADMIN · AUDITOR가 추적할 수 있습니다</li>
            </ul>
          </div>

          <div
            class="dropzone"
            :class="{ dragging: isDragging, 'has-file': !!selectedFile }"
            @dragover.prevent="isDragging = true"
            @dragleave="isDragging = false"
            @drop.prevent="onDrop"
            @click="fileInputRef?.click()"
          >
            <input ref="fileInputRef" type="file" accept=".docx,.xlsx,.pptx" hidden @change="onFileInput" />

            <template v-if="!selectedFile">
              <div class="dz-icon">📁</div>
              <div class="dz-text">파일을 드래그하거나 클릭해서 선택</div>
              <div class="dz-hint">DOCX · XLSX · PPTX</div>
            </template>
            <template v-else>
              <div class="file-row">
                <span class="file-emoji">{{ getFileEmoji(selectedFile.name) }}</span>
                <div class="file-info">
                  <div class="file-name">{{ selectedFile.name }}</div>
                  <div class="file-size">{{ formatSize(selectedFile.size) }}</div>
                </div>
                <button class="file-rm" @click.stop="selectedFile = null">✕</button>
              </div>
            </template>
          </div>

          <div class="card-footer">
            <button class="btn-outline" @click="currentStep = 1">← 이전</button>
            <button class="btn-primary" :disabled="!selectedFile || uploading" @click="startCdr">
              <el-icon v-if="uploading" class="is-loading"><Loading /></el-icon>
              {{ uploading ? '업로드 중...' : 'CDR 처리 시작 →' }}
            </button>
          </div>
        </div>

        <!-- ─────── Step 3: CDR 처리 중 ─────── -->
        <div v-else-if="currentStep === 3" key="s3" class="card">
          <div class="card-head">
            <div class="card-icon orange"><el-icon class="is-loading"><Loading /></el-icon></div>
            <div>
              <div class="card-title">CDR 처리 중</div>
              <div class="card-desc">파일을 분석하고 위험 요소를 제거합니다 · 2초마다 상태를 자동으로 조회합니다</div>
            </div>
          </div>

          <!-- CDR 파이프라인 시각화 -->
          <div class="pipeline">
            <div class="ps" :class="{ active: pipelineState.analyze }">
              <div class="ps-emoji">🔍</div>
              <div class="ps-name">분석</div>
              <div class="ps-sub">위험 요소 탐지</div>
            </div>
            <div class="pipe-arrow" :class="{ active: pipelineState.sanitize }">→</div>
            <div class="ps" :class="{ active: pipelineState.sanitize }">
              <div class="ps-emoji">🛡️</div>
              <div class="ps-name">무해화</div>
              <div class="ps-sub">위험 요소 제거</div>
            </div>
            <div class="pipe-arrow" :class="{ active: pipelineState.reconstruct }">→</div>
            <div class="ps" :class="{ active: pipelineState.reconstruct }">
              <div class="ps-emoji">✅</div>
              <div class="ps-name">재구성</div>
              <div class="ps-sub">안전한 파일 생성</div>
            </div>
          </div>

          <!-- 처리 상태 배지 -->
          <div class="status-badge" :class="fileStatus.toLowerCase()">
            <el-icon v-if="fileStatus === 'PROCESSING'" class="is-loading"><Loading /></el-icon>
            {{
              fileStatus === 'UPLOADED' ? '⏳ 처리 대기 중' :
              fileStatus === 'PROCESSING' ? '⚙️ 처리 중' : fileStatus
            }}
          </div>

          <div v-if="pollErrMsg" class="hint" style="color: var(--el-color-danger); border: 1px solid currentColor; margin-bottom: 15px;">
            <strong>연결 오류:</strong> {{ pollErrMsg }} (API Key 권한을 확인하세요)
          </div>

          <!-- 실시간 이벤트 스트림 -->
          <div class="event-log">
            <div class="el-header">📡 실시간 이벤트 스트림</div>
            <div class="el-body">
              <div v-if="!events.length" class="el-empty">이벤트 대기 중...</div>
              <TransitionGroup name="ev-add">
                <div v-for="ev in events" :key="ev.id" class="ev-row">
                  <span class="ev-time">{{ formatEventTime(ev.createdAt) }}</span>
                  <span class="ev-type" :class="ev.eventType.toLowerCase()">{{ getEventLabel(ev.eventType) }}</span>
                  <span class="ev-payload">{{ formatEventPayload(ev.payload) }}</span>
                </div>
              </TransitionGroup>
            </div>
          </div>
        </div>

        <!-- ─────── Step 4: 결과 확인 ─────── -->
        <div v-else-if="currentStep === 4" key="s4" class="card">
          <div class="card-head">
            <div class="card-icon" :class="fileStatus === 'DONE' ? 'green' : 'red'">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div>
              <div class="card-title">CDR 처리 {{ fileStatus === 'DONE' ? '완료' : '실패' }}</div>
              <div class="card-desc">무해화 결과를 확인하고 안전한 파일을 다운로드합니다</div>
            </div>
          </div>

          <!-- 결과 통계 -->
          <div class="stats-row">
            <div class="stat-card">
              <div class="stat-label">처리 결과</div>
              <div class="stat-val" :class="fileStatus === 'DONE' ? 'ok' : 'ng'">
                {{ fileStatus === 'DONE' ? '✅ 성공' : '❌ 실패' }}
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-label">소요 시간</div>
              <div class="stat-val">{{ processingTime }}초</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">이벤트 수</div>
              <div class="stat-val">{{ events.length }}건</div>
            </div>
          </div>

          <!-- 전체 이벤트 이력 -->
          <div class="event-log">
            <div class="el-header">📋 전체 처리 이력</div>
            <div class="el-body">
              <div v-for="ev in events" :key="ev.id" class="ev-row">
                <span class="ev-time">{{ formatEventTime(ev.createdAt) }}</span>
                <span class="ev-type" :class="ev.eventType.toLowerCase()">{{ getEventLabel(ev.eventType) }}</span>
                <span class="ev-payload">{{ formatEventPayload(ev.payload) }}</span>
              </div>
            </div>
          </div>

          <div class="card-footer">
            <button class="btn-outline" @click="resetDemo">🔄 처음부터 다시</button>
            <button v-if="fileStatus === 'DONE'" class="btn-primary" @click="downloadFile">
              <el-icon><Download /></el-icon>
              무해화 파일 다운로드
            </button>
          </div>
        </div>

      </transition>
    </main>
  </div>
</template>

<style scoped>
/* ── 루트 ── */
.demo-root {
  min-height: 100vh;
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
}

/* ── 헤더 ── */
.demo-header {
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 60%, #4338ca 100%);
  color: #fff;
  padding: 28px 48px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  flex-shrink: 0;
}

.demo-header-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.env-badge {
  display: inline-flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 20px;
  padding: 3px 12px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.8px;
  width: fit-content;
}

.demo-title {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
  margin: 0;
}

.demo-sub {
  font-size: 13px;
  opacity: 0.72;
  margin: 0;
}

.back-btn {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.22);
  color: #fff;
  padding: 8px 18px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: background 0.2s;
  font-family: inherit;
}
.back-btn:hover { background: rgba(255, 255, 255, 0.22); }

/* ── 스텝 인디케이터 ── */
.stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 40px;
  background: var(--el-bg-color-overlay);
  border-bottom: 1px solid var(--el-border-color-lighter);
  gap: 0;
  flex-shrink: 0;
}

.sn {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sn-circle {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 2px solid var(--el-border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-placeholder);
  background: var(--el-bg-color);
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.sn.active .sn-circle {
  border-color: #6366f1;
  background: #6366f1;
  color: #fff;
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.15);
}

.sn.done .sn-circle {
  border-color: #10b981;
  background: #10b981;
  color: #fff;
}

.sn-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
  transition: color 0.3s;
}
.sn.active .sn-label { color: #6366f1; font-weight: 700; }
.sn.done .sn-label { color: #10b981; }

.sn-line {
  width: 72px;
  height: 2px;
  background: var(--el-border-color-lighter);
  margin: 0 8px;
  transition: background 0.4s;
}
.sn-line.done { background: #10b981; }

/* ── 메인 영역 ── */
.demo-main {
  flex: 1;
  max-width: 740px;
  width: 100%;
  margin: 0 auto;
  padding: 36px 20px 48px;
}

/* ── 카드 ── */
.card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 18px;
  padding: 36px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

.card-head {
  display: flex;
  align-items: flex-start;
  gap: 18px;
}

.card-icon {
  width: 50px;
  height: 50px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.card-icon.indigo { background: rgba(99, 102, 241, 0.1); color: #6366f1; }
.card-icon.blue   { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
.card-icon.orange { background: rgba(249, 115, 22, 0.1); color: #f97316; }
.card-icon.green  { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.card-icon.red    { background: rgba(239, 68, 68, 0.1);  color: #ef4444; }

.card-title {
  font-size: 19px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  letter-spacing: -0.3px;
}

.card-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 5px;
  line-height: 1.5;
}

/* ── 정보 박스 ── */
.info-box {
  background: var(--el-fill-color-light);
  border-left: 3px solid #6366f1;
  border-radius: 0 10px 10px 0;
  padding: 16px 20px;
}

.info-box-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
}

.info-box ul {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-box li {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.55;
}

.info-box b { color: #6366f1; font-weight: 600; }

/* ── API 키 입력 행 ── */
.key-row {
  display: flex;
  gap: 12px;
}

.key-input {
  flex: 1;
  height: 46px;
  border: 1.5px solid var(--el-border-color);
  border-radius: 10px;
  padding: 0 16px;
  font-size: 13px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  outline: none;
  transition: border-color 0.2s;
}
.key-input:focus { border-color: #6366f1; }
.key-input::placeholder { color: var(--el-text-color-placeholder); }

.btn-connect {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 22px;
  height: 46px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  background: #6366f1;
  color: #fff;
  white-space: nowrap;
  transition: background 0.2s, opacity 0.2s;
  flex-shrink: 0;
}
.btn-connect:hover:not(:disabled) { background: #4f46e5; }
.btn-connect.success { background: #10b981; }
.btn-connect:disabled { opacity: 0.6; cursor: not-allowed; }

/* ── 힌트 ── */
.hint {
  font-size: 12.5px;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-lighter);
  padding: 11px 16px;
  border-radius: 8px;
  line-height: 1.5;
}
.hint b { color: var(--el-text-color-secondary); }

/* ── 드롭존 ── */
.dropzone {
  border: 2px dashed var(--el-border-color);
  border-radius: 14px;
  padding: 44px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--el-fill-color-lighter);
  user-select: none;
}
.dropzone:hover,
.dropzone.dragging { border-color: #6366f1; background: rgba(99, 102, 241, 0.04); }
.dropzone.has-file { border-style: solid; border-color: #10b981; background: rgba(16, 185, 129, 0.04); }

.dz-icon { font-size: 38px; margin-bottom: 10px; }
.dz-text { font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); }
.dz-hint { font-size: 12px; color: var(--el-text-color-placeholder); margin-top: 5px; }

.file-row {
  display: flex;
  align-items: center;
  gap: 14px;
  text-align: left;
}
.file-emoji { font-size: 32px; flex-shrink: 0; }
.file-info { flex: 1; min-width: 0; }
.file-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  word-break: break-all;
}
.file-size { font-size: 12px; color: var(--el-text-color-placeholder); margin-top: 2px; }
.file-rm {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--el-text-color-placeholder);
  font-size: 16px;
  padding: 6px;
  flex-shrink: 0;
  border-radius: 6px;
  transition: background 0.2s, color 0.2s;
}
.file-rm:hover { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

/* ── 버튼 공통 ── */
.card-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 4px;
}

.btn-primary,
.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 44px;
  padding: 0 22px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
  border: 1.5px solid;
}
.btn-primary { background: #6366f1; border-color: #6366f1; color: #fff; }
.btn-primary:hover:not(:disabled) { background: #4f46e5; border-color: #4f46e5; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-outline { background: transparent; border-color: var(--el-border-color); color: var(--el-text-color-regular); }
.btn-outline:hover { border-color: #6366f1; color: #6366f1; }

/* ── CDR 파이프라인 ── */
.pipeline {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: var(--el-fill-color-lighter);
  border-radius: 14px;
  padding: 22px 16px;
}

.ps {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 22px;
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
  border: 1.5px solid var(--el-border-color-lighter);
  min-width: 110px;
  opacity: 0.4;
  transition: all 0.5s ease;
}
.ps.active {
  border-color: #6366f1;
  background: rgba(99, 102, 241, 0.07);
  opacity: 1;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.15);
}
.ps-emoji { font-size: 26px; }
.ps-name { font-size: 13px; font-weight: 700; color: var(--el-text-color-primary); }
.ps-sub { font-size: 11px; color: var(--el-text-color-placeholder); text-align: center; }

.pipe-arrow {
  font-size: 22px;
  color: var(--el-text-color-placeholder);
  transition: color 0.4s;
  flex-shrink: 0;
}
.pipe-arrow.active { color: #6366f1; }

/* ── 상태 배지 ── */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  align-self: center;
}
.status-badge.uploaded   { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }
.status-badge.processing { background: rgba(99, 102, 241, 0.1); color: #6366f1; }
.status-badge.done       { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.status-badge.fail       { background: rgba(239, 68, 68, 0.1);  color: #ef4444; }

/* ── 이벤트 로그 ── */
.event-log {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  overflow: hidden;
}

.el-header {
  padding: 11px 18px;
  font-size: 13px;
  font-weight: 600;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
}

.el-body {
  max-height: 220px;
  overflow-y: auto;
  padding: 6px 0;
}

.el-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.ev-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 18px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  font-size: 12.5px;
}
.ev-row:last-child { border-bottom: none; }

.ev-time {
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
  font-family: monospace;
  flex-shrink: 0;
}

.ev-type {
  padding: 2px 9px;
  border-radius: 5px;
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}
.ev-type.queued { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }
.ev-type.start  { background: rgba(99, 102, 241, 0.1); color: #6366f1; }
.ev-type.done   { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.ev-type.fail   { background: rgba(239, 68, 68, 0.1);  color: #ef4444; }

.ev-payload {
  color: var(--el-text-color-secondary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 결과 통계 ── */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.stat-card {
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 18px 16px;
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-bottom: 8px;
}

.stat-val {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.stat-val.ok { color: #10b981; }
.stat-val.ng { color: #ef4444; }

/* ── 트랜지션 ── */
.slide-fade-enter-active { transition: opacity 0.28s ease, transform 0.28s ease; }
.slide-fade-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.slide-fade-enter-from { opacity: 0; transform: translateX(28px); }
.slide-fade-leave-to  { opacity: 0; transform: translateX(-28px); }

.ev-add-enter-active { transition: all 0.3s ease; }
.ev-add-enter-from   { opacity: 0; transform: translateY(-8px); }

code {
  background: var(--el-fill-color);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
  color: #6366f1;
}
</style>
