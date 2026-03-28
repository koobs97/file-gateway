<!--
  AdminView
  - ROLE_ADMIN 전용 시스템 관리 페이지
  - 상단에 파일 처리 통계(전체/완료/실패/성공률)를 카드 형태로 표시한다
  - 탭 구성: 사용자 관리 / API 클라이언트 관리 / 감사 로그
  - 사용자 관리: 목록 조회, 신규 생성, 역할 변경, 활성/비활성 토글
  - API 클라이언트 관리: 목록 조회, 생성, API 키 표시/숨김/복사, 비활성화
  - 감사 로그: 파일 처리 단계별 로그 조회 (페이지네이션 포함)
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'
import type { UserSummaryResponse, ApiClientResponse, ProcessLogResponse, AdminPage } from '../types/admin'
import type { FileStatistics } from '../types/file'
// ── 통계 아이콘 임포트
import { Warning, Delete, Loading, Close } from '@element-plus/icons-vue'

// ── API 클라이언트 비활성화 다이얼로그 상태 ──
const deactivateDialog = reactive({
  visible: false,
  targetId: null as number | null,
  targetName: '',
  loading: false
})

/**
 * 비활성화 다이얼로그 열기
 */
function openDeactivateDialog(row: ApiClientResponse) {
  deactivateDialog.targetId = row.id
  deactivateDialog.targetName = row.clientName
  deactivateDialog.visible = true
}

/**
 * 최종 비활성화 실행
 */
async function confirmDeactivate() {
  if (deactivateDialog.targetId === null) return
  
  deactivateDialog.loading = true
  try {
    await adminApi.deactivateApiClient(deactivateDialog.targetId)
    ElMessage.success('API 클라이언트가 비활성화되었습니다.')
    deactivateDialog.visible = false
    loadClients()
  } finally {
    deactivateDialog.loading = false
  }
}


// ── 통계 ─────────────────────────────────────
/** 파일 처리 통계 데이터 */
const stats = ref<FileStatistics | null>(null)

/** 통계 로딩 상태 */
const statsLoading = ref(false)

/**
 * 파일 처리 통계를 서버에서 조회한다.
 */
async function loadStats() {
  statsLoading.value = true
  try {
    const res = await adminApi.getStatistics()
    stats.value = res.data.data
  } finally {
    statsLoading.value = false
  }
}

// ── 사용자 관리 (탭 B) ────────────────────────
/** 사용자 목록 페이지 데이터 */
const users = ref<AdminPage<UserSummaryResponse> | null>(null)

/** 사용자 목록 로딩 상태 */
const usersLoading = ref(false)

/** 사용자 추가 다이얼로그 표시 여부 */
const createUserDialogVisible = ref(false)

/** 사용자 생성 API 호출 중 로딩 상태 */
const createUserLoading = ref(false)

/** 사용자 추가 폼 데이터 */
const createUserForm = reactive({ username: '', password: '', role: 'ROLE_END_USER' })

/** Element Plus 폼 인스턴스 참조 (유효성 검사 호출용) */
const createUserFormRef = ref()

/** 사용자 추가 폼 유효성 검사 규칙 */
const createUserRules = {
  username: [
    { required: true, message: '아이디를 입력하세요.', trigger: 'blur' },
    { min: 3, max: 50, message: '3~50자여야 합니다.', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '초기 비밀번호를 입력하세요.', trigger: 'blur' },
    { min: 8, message: '8자 이상이어야 합니다.', trigger: 'blur' },
  ],
  role: [{ required: true, message: '역할을 선택하세요.', trigger: 'change' }],
}

/** 역할 선택 드롭다운 옵션 목록 */
const ROLE_OPTIONS = [
  { label: '관리자', value: 'ROLE_ADMIN' },
  { label: '감사자', value: 'ROLE_AUDITOR' },
  { label: '일반 사용자', value: 'ROLE_END_USER' },
]

/**
 * 지정한 페이지 번호의 사용자 목록을 조회한다.
 *
 * @param page 0-based 페이지 번호 (기본값: 0)
 */
async function loadUsers(page = 0) {
  usersLoading.value = true
  try {
    const res = await adminApi.getUsers(page)
    users.value = res.data.data
  } finally {
    usersLoading.value = false
  }
}

/**
 * 사용자 추가 폼을 초기화한다.
 * 유효성 검사 오류 메시지를 제거하고 입력값을 기본값으로 되돌린다.
 */
const resetUserForm = () => {
  // 1. 유효성 검사 메시지 초기화 및 폼 데이터 리셋
  if (createUserFormRef.value) {
    createUserFormRef.value.resetFields()
  }
  // 2. reactive 객체 수동 초기화 (혹시 모를 잔상 방지)
  createUserForm.username = ''
  createUserForm.password = ''
  createUserForm.role = 'ROLE_END_USER'
}

/**
 * API 클라이언트 생성 폼의 이름 입력값을 초기화한다.
 */
const resetClientForm = () => {
  newClientName.value = ''
}

/**
 * 사용자 추가 폼의 유효성 검사를 수행하고 사용자 생성 API를 호출한다.
 * 성공 시 다이얼로그를 닫고 목록을 새로고침한다.
 */
async function handleCreateUser() {
  if (!createUserFormRef.value) return
  try {
    await createUserFormRef.value.validate()
    createUserLoading.value = true
    await adminApi.createUser(createUserForm.username, createUserForm.password, createUserForm.role)
    ElMessage.success('사용자가 생성되었습니다.')
    createUserDialogVisible.value = false // 닫히면서 @closed 호출됨
    loadUsers()
  } catch (err: any) {
    // ... 에러 처리 ...
  } finally {
    createUserLoading.value = false
  }
}

/**
 * 선택된 사용자의 역할을 변경하고 성공 메시지를 표시한다.
 *
 * @param user 역할을 변경할 사용자 객체
 * @param newRole 변경할 역할 코드 (예: 'ROLE_ADMIN')
 */
async function handleRoleChange(user: UserSummaryResponse, newRole: string) {
  await adminApi.updateRole(user.id, newRole)
  user.role = newRole
  ElMessage.success('역할이 변경되었습니다.')
}

/**
 * 사용자 계정의 활성/비활성 상태를 토글하고 성공 메시지를 표시한다.
 *
 * @param user 상태를 토글할 사용자 객체
 */
async function handleStatusToggle(user: UserSummaryResponse) {
  const newActive = !user.active
  await adminApi.updateStatus(user.id, newActive)
  user.active = newActive
  ElMessage.success(newActive ? '계정이 활성화되었습니다.' : '계정이 비활성화되었습니다.')
}

// ── API 클라이언트 관리 (탭 C) ─────────────────
/** API 클라이언트 목록 페이지 데이터 */
const clients = ref<AdminPage<ApiClientResponse> | null>(null)

/** API 클라이언트 목록 로딩 상태 */
const clientsLoading = ref(false)

/** API 클라이언트 생성 다이얼로그 표시 여부 */
const createDialogVisible = ref(false)

/** 신규 API 클라이언트 이름 입력값 */
const newClientName = ref('')

/** API 클라이언트 생성 API 호출 중 로딩 상태 */
const createLoading = ref(false)

/** API 키를 평문으로 표시 중인 클라이언트 ID 집합 */
const revealedKeys = ref<Set<number>>(new Set())

/**
 * 지정한 페이지 번호의 API 클라이언트 목록을 조회한다.
 *
 * @param page 0-based 페이지 번호 (기본값: 0)
 */
async function loadClients(page = 0) {
  clientsLoading.value = true
  try {
    const res = await adminApi.getApiClients(page)
    clients.value = res.data.data
  } finally {
    clientsLoading.value = false
  }
}

/**
 * 신규 API 클라이언트를 생성한다.
 * 이름이 비어 있으면 경고 메시지를 표시하고 중단한다.
 * 성공 시 다이얼로그를 닫고 목록을 새로고침한다.
 */
async function handleCreateClient() {
  if (!newClientName.value.trim()) {
    ElMessage.warning('클라이언트 이름을 입력하세요.')
    return
  }
  createLoading.value = true
  try {
    await adminApi.createApiClient(newClientName.value.trim())
    ElMessage.success('API 클라이언트가 생성되었습니다.')
    createDialogVisible.value = false // 닫히면서 @closed 호출됨
    loadClients()
  } finally {
    createLoading.value = false
  }
}


/**
 * 특정 클라이언트의 API 키 표시 상태를 토글한다.
 *
 * @param id 표시 상태를 토글할 API 클라이언트 ID
 */
function toggleKeyReveal(id: number) {
  if (revealedKeys.value.has(id)) revealedKeys.value.delete(id)
  else revealedKeys.value.add(id)
}

/**
 * API 키의 앞 8자만 표시하고 나머지를 마스킹한다.
 *
 * @param key 마스킹할 API 키 문자열
 * @returns 앞 8자 + 마스킹 문자열
 */
function maskKey(key: string) {
  return key.slice(0, 8) + '••••••••••••••••••••'
}

/**
 * API 키를 클립보드에 복사하고 성공 메시지를 표시한다.
 *
 * @param key 복사할 API 키 문자열
 */
async function copyKey(key: string) {
  await navigator.clipboard.writeText(key)
  ElMessage.success('API 키가 복사되었습니다.')
}

// ── 감사 로그 (탭 D) ─────────────────────────
/** 감사 로그 목록 페이지 데이터 */
const logs = ref<AdminPage<ProcessLogResponse> | null>(null)

/** 감사 로그 목록 로딩 상태 */
const logsLoading = ref(false)

/** 로그 처리 결과 상태 코드를 한국어 레이블 및 Element Plus 태그 타입으로 매핑한다 */
const LOG_STATUS_MAP: Record<string, { label: string; type: 'success' | 'danger' | 'warning' | 'info' }> = {
  SUCCESS: { label: '성공', type: 'success' },
  FAILURE: { label: '실패', type: 'danger' },
  SKIP:    { label: '건너뜀', type: 'warning' },
}

/**
 * 지정한 페이지 번호의 감사 로그 목록을 조회한다.
 *
 * @param page 0-based 페이지 번호 (기본값: 0)
 */
async function loadLogs(page = 0) {
  logsLoading.value = true
  try {
    const res = await adminApi.getProcessLogs(page)
    logs.value = res.data.data
  } finally {
    logsLoading.value = false
  }
}

/**
 * 날짜 문자열을 `YYYY-MM-DD HH:mm` 형식의 한 줄 문자열로 변환한다.
 *
 * @param dt ISO 날짜 문자열
 * @returns 콤팩트 날짜+시각 문자열
 */
function formatDateTime(dt: string): string {
  const d = new Date(dt)
  const date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return `${date} ${time}`
}

onMounted(() => {
  loadStats()
  loadUsers()
  loadClients()
  loadLogs()
})
</script>

<template>
  <div class="admin-page">
    <!-- 통계 요약 (상단 고정) -->
    <el-row :gutter="14" class="stats-row" v-loading="statsLoading">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-label">활성 파일</div>
            <div class="stat-value">{{ stats?.total ?? '-' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-label">완료 (DONE)</div>
            <div class="stat-value success">{{ stats?.done ?? '-' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-label">실패 (FAIL)</div>
            <div class="stat-value danger">{{ stats?.fail ?? '-' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-label">성공률</div>
            <div class="stat-value">
              {{ stats != null ? stats.successRate.toFixed(1) + '%' : '-' }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 관리 탭 -->
    <el-card class="admin-main-card">
      <el-tabs type="border-card" class="admin-tabs">

        <!-- ── 탭 B: 사용자 관리 ─────────────── -->
        <el-tab-pane label="사용자 관리">
          <div class="tab-header">
            <span class="tab-title">등록된 사용자</span>
            <div style="display:flex;">
              <el-button size="small" type="primary" @click="createUserDialogVisible = true">
                + 사용자 추가
              </el-button>
              <el-button style="margin-left: 8px;" size="small" @click="loadUsers()">새로고침</el-button>
            </div>
          </div>
          <el-table v-loading="usersLoading" :data="users?.content" stripe>
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column prop="username" label="아이디" width="140" />
            <el-table-column label="역할" width="180">
              <template #default="{ row }">
                <el-select
                  :model-value="row.role"
                  size="small"
                  style="width: 155px"
                  @change="(val: string) => handleRoleChange(row, val)"
                >
                  <el-option
                    v-for="opt in ROLE_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="상태" minWidth="110">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.active"
                  active-text="활성"
                  inactive-text="비활성"
                  inline-prompt
                  @change="() => handleStatusToggle(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="가입일" width="148">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-if="users && users.totalPages > 1"
              :current-page="(users.number ?? 0) + 1"
              :page-size="users.size"
              :total="users.totalElements"
              layout="prev, pager, next, total"
              @current-change="(p: number) => loadUsers(p - 1)"
            />
          </div>
        </el-tab-pane>

        <!-- ── 탭 C: API 클라이언트 관리 ─────── -->
        <el-tab-pane label="API 클라이언트">
          <div class="tab-header">
            <span class="tab-title">API 클라이언트 목록</span>
            <el-button size="small" type="primary" @click="createDialogVisible = true">
              + 클라이언트 생성
            </el-button>
          </div>
          <el-table v-loading="clientsLoading" :data="clients?.content" stripe>
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column prop="clientName" label="클라이언트명" width="180" />
            <el-table-column label="API 키" min-width="260">
              <template #default="{ row }">
                <div class="api-key-cell">
                  <code class="api-key-text">
                    {{ revealedKeys.has(row.id) ? row.apiKey : maskKey(row.apiKey) }}
                  </code>
                  <button class="key-btn view" @click="toggleKeyReveal(row.id)">
                    {{ revealedKeys.has(row.id) ? '숨김' : '보기' }}
                  </button>
                  <button class="key-btn copy" @click="copyKey(row.apiKey)">
                    복사
                  </button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="상태" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'ACTIVE' ? '활성' : '비활성' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="생성일" width="148">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="작업" width="100">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  type="danger"
                  size="small"
                  plain
                  @click="openDeactivateDialog(row)" 
                >
                  비활성화
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-if="clients && clients.totalPages > 1"
              :current-page="(clients.number ?? 0) + 1"
              :page-size="clients.size"
              :total="clients.totalElements"
              layout="prev, pager, next, total"
              @current-change="(p: number) => loadClients(p - 1)"
            />
          </div>
        </el-tab-pane>

        <!-- ── 탭 D: 감사 로그 ───────────────── -->
        <el-tab-pane label="감사 로그">
          <div class="tab-header">
            <span class="tab-title">파일 처리 로그</span>
            <el-button size="small" @click="loadLogs()">새로고침</el-button>
          </div>
          <el-table v-loading="logsLoading" :data="logs?.content" stripe>
            <el-table-column prop="fileId" label="파일 ID" width="80" />
            <el-table-column prop="step" label="처리 단계" width="130">
              <template #default="{ row }">
                <el-tag type="info" size="small">{{ row.step }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="결과" width="90">
              <template #default="{ row }">
                <el-tag
                  :type="LOG_STATUS_MAP[row.status]?.type ?? 'info'"
                  size="small"
                >
                  {{ LOG_STATUS_MAP[row.status]?.label ?? row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="메시지" min-width="200" show-overflow-tooltip />
            <el-table-column label="시각" width="148">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-if="logs && logs.totalPages > 1"
              :current-page="(logs.number ?? 0) + 1"
              :page-size="logs.size"
              :total="logs.totalElements"
              layout="prev, pager, next, total"
              @current-change="(p: number) => loadLogs(p - 1)"
            />
          </div>
        </el-tab-pane>

      </el-tabs>
    </el-card>

    <!-- 사용자 추가 다이얼로그 -->
    <el-dialog
      v-model="createUserDialogVisible"
      title="사용자 추가"
      width="420px"
      @closed="resetUserForm"
    >
      <el-form
        ref="createUserFormRef"
        :model="createUserForm"
        :rules="createUserRules"
        label-position="top"
      >
        <el-form-item label="아이디" prop="username">
          <el-input v-model="createUserForm.username" placeholder="3~50자" autocomplete="off" />
        </el-form-item>
        <el-form-item label="초기 비밀번호" prop="password">
          <el-input v-model="createUserForm.password" type="password" placeholder="8자 이상" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="역할" prop="role">
          <el-select v-model="createUserForm.role" style="width:100%">
            <el-option v-for="opt in ROLE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="create-hint">
        <el-icon><InfoFilled /></el-icon>
        생성된 계정은 최초 로그인 시 비밀번호 변경이 강제됩니다.
      </div>
      <template #footer>
        <el-button @click="createUserDialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="createUserLoading" @click="handleCreateUser">
          생성
        </el-button>
      </template>
    </el-dialog>

    <!-- API 클라이언트 생성 다이얼로그 -->
    <el-dialog
      v-model="createDialogVisible"
      title="API 클라이언트 생성"
      width="400px"
      @closed="resetClientForm"
    >
      <el-form label-position="top">
        <el-form-item label="클라이언트 이름">
          <el-input
            v-model="newClientName"
            placeholder="예: my-service-prod"
            @keyup.enter="handleCreateClient"
          />
        </el-form-item>
      </el-form>
      <div class="create-hint">
        <el-icon><InfoFilled /></el-icon>
        API 키는 자동으로 생성됩니다. 생성 후 안전한 곳에 보관하세요.
      </div>
      <template #footer>
        <el-button @click="createDialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreateClient">
          생성
        </el-button>
      </template>
    </el-dialog>

    <!-- API 클라이언트 비활성화 확인 다이얼로그 -->
    <el-dialog
      v-model="deactivateDialog.visible"
      :show-close="false"
      width="380px"
      align-center
      class="confirm-dialog"
    >
      <div class="dc-body">
        <div class="dc-icon-wrap warning"> <!-- 경고 의미로 주황색 테마 -->
          <el-icon class="dc-big-icon"><Warning /></el-icon>
        </div>
        <p class="dc-title">클라이언트 비활성화</p>
        <p class="dc-desc">
          <strong>'{{ deactivateDialog.targetName }}'</strong>을(를) 비활성화하시겠습니까?<br>
          비활성 시 해당 API 키를 통한 접속이 즉시 차단됩니다.
        </p>
      </div>
      <template #footer>
        <div class="dc-footer">
          <button class="dc-cancel-btn" :disabled="deactivateDialog.loading" @click="deactivateDialog.visible = false">
            취소
          </button>
          <button class="dc-confirm-btn warning" :disabled="deactivateDialog.loading" @click="confirmDeactivate">
            <el-icon v-if="deactivateDialog.loading" class="is-loading"><Loading /></el-icon>
            <el-icon v-else><Close /></el-icon>
            {{ deactivateDialog.loading ? '처리 중...' : '비활성화 실행' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ─── 전체 레이아웃 — 뷰포트 안에 가두기 ─────── */
/* App.vue: header 54px + el-main padding 48px(24*2) = 102px */
.admin-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 102px);
  overflow: hidden;
}

.stats-row {
  flex-shrink: 0;
  margin-bottom: 16px;
}

/* 탭 카드: 남은 공간 모두 차지 */
.admin-main-card {
  flex: 1;
  min-height: 0;
}
:deep(.admin-main-card.el-card) {
  display: flex;
  flex-direction: column;
  height: 100%;
}
:deep(.admin-main-card > .el-card__body) {
  flex: 1;
  min-height: 0;
  padding: 0 !important;
  display: flex;
  flex-direction: column;
}

/* el-tabs: 카드 바디를 채움 */
:deep(.admin-tabs.el-tabs--border-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: none;
  box-shadow: none;
}
:deep(.admin-tabs .el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 18px;
}
:deep(.admin-tabs .el-tab-pane) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* ─── 통계 카드 */
.stat-card :deep(.el-card__body) {
  padding: 16px !important;
}
.stat-inner { text-align: center; }
.stat-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 6px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}
.stat-value {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}
.stat-value.success { color: var(--color-success); }
.stat-value.danger  { color: var(--color-danger); }

/* 탭 */
.admin-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.tab-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

/* API 키 */
.api-key-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.api-key-text {
  font-family: monospace;
  font-size: 12px;
  color: var(--primary);
  background: var(--primary-light);
  padding: 2px 6px;
  border-radius: 4px;
}

/* 보기/복사 버튼 — 다크/라이트 모드 모두 명시적 색상 */
.key-btn {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 11.5px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.18s;
  white-space: nowrap;
  flex-shrink: 0;
}
.key-btn.view {
  background: transparent;
  border: 1px solid var(--el-border-color);
  color: var(--el-text-color-regular);
}
.key-btn.view:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.key-btn.copy {
  background: rgba(99, 102, 241, 0.12);
  border: 1px solid rgba(99, 102, 241, 0.35);
  color: #818cf8;
}
.key-btn.copy:hover {
  background: rgba(99, 102, 241, 0.22);
  border-color: #818cf8;
  color: #a5b4fc;
}

/* 생성 힌트 */
.create-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 8px;
}

/* 페이지 */
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}

:deep(.confirm-dialog) {
  border-radius: 16px !important;
  padding: 0 !important;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color-overlay) !important;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.15) !important;
}
:deep(.confirm-dialog .el-dialog__header) { display: none !important; }
:deep(.confirm-dialog .el-dialog__body)   { padding: 32px 28px 20px !important; }
:deep(.confirm-dialog .el-dialog__footer) { padding: 0 28px 28px !important; }

.dc-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}
.dc-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
/* 주황색 경고 테마 */
.dc-icon-wrap.warning { background: rgba(245, 158, 11, 0.12); color: #f59e0b; }
.dc-big-icon { font-size: 26px; }

.dc-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0;
  letter-spacing: -0.5px;
}
.dc-desc {
  font-size: 13.5px;
  color: var(--el-text-color-secondary);
  margin: 0;
  line-height: 1.6;
}
.dc-desc strong { color: var(--el-text-color-primary); font-weight: 700; }

.dc-footer { display: flex; gap: 10px; }
.dc-cancel-btn, .dc-confirm-btn {
  flex: 1; height: 42px; border-radius: 10px; font-size: 14px; font-weight: 600;
  cursor: pointer; display: inline-flex; align-items: center; justify-content: center;
  gap: 6px; border: 1px solid; transition: all 0.2s;
}

.dc-cancel-btn {
  background: var(--el-fill-color-light); border-color: var(--el-border-color-lighter);
  color: var(--el-text-color-regular);
}
.dc-cancel-btn:hover { background: var(--el-fill-color); color: var(--el-text-color-primary); }

.dc-confirm-btn.warning {
  background: #f59e0b; border-color: #f59e0b; color: #fff;
}
.dc-confirm-btn.warning:hover { background: #d97706; border-color: #d97706; }

.dc-cancel-btn:disabled, .dc-confirm-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
