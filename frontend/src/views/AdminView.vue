<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'
import type { UserSummaryResponse, ApiClientResponse, ProcessLogResponse, AdminPage } from '../types/admin'
import type { FileStatistics } from '../types/file'

// ── 통계 ─────────────────────────────────────
const stats = ref<FileStatistics | null>(null)
const statsLoading = ref(false)

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
const users = ref<AdminPage<UserSummaryResponse> | null>(null)
const usersLoading = ref(false)
const createUserDialogVisible = ref(false)
const createUserLoading = ref(false)
const createUserForm = reactive({ username: '', password: '', role: 'ROLE_END_USER' })
const createUserFormRef = ref()

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

const ROLE_OPTIONS = [
  { label: '관리자', value: 'ROLE_ADMIN' },
  { label: '감사자', value: 'ROLE_AUDITOR' },
  { label: '일반 사용자', value: 'ROLE_END_USER' },
]


async function loadUsers(page = 0) {
  usersLoading.value = true
  try {
    const res = await adminApi.getUsers(page)
    users.value = res.data.data
  } finally {
    usersLoading.value = false
  }
}

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

const resetClientForm = () => {
  newClientName.value = ''
}

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

async function handleRoleChange(user: UserSummaryResponse, newRole: string) {
  await adminApi.updateRole(user.id, newRole)
  user.role = newRole
  ElMessage.success('역할이 변경되었습니다.')
}

async function handleStatusToggle(user: UserSummaryResponse) {
  const newActive = !user.active
  await adminApi.updateStatus(user.id, newActive)
  user.active = newActive
  ElMessage.success(newActive ? '계정이 활성화되었습니다.' : '계정이 비활성화되었습니다.')
}

// ── API 클라이언트 관리 (탭 C) ─────────────────
const clients = ref<AdminPage<ApiClientResponse> | null>(null)
const clientsLoading = ref(false)
const createDialogVisible = ref(false)
const newClientName = ref('')
const createLoading = ref(false)
const revealedKeys = ref<Set<number>>(new Set())

async function loadClients(page = 0) {
  clientsLoading.value = true
  try {
    const res = await adminApi.getApiClients(page)
    clients.value = res.data.data
  } finally {
    clientsLoading.value = false
  }
}

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

async function handleDeactivateClient(id: number) {
  await ElMessageBox.confirm('API 클라이언트를 비활성화하시겠습니까?', '확인', { type: 'warning' })
  await adminApi.deactivateApiClient(id)
  ElMessage.success('비활성화되었습니다.')
  loadClients()
}

function toggleKeyReveal(id: number) {
  if (revealedKeys.value.has(id)) revealedKeys.value.delete(id)
  else revealedKeys.value.add(id)
}

function maskKey(key: string) {
  return key.slice(0, 8) + '••••••••••••••••••••'
}

async function copyKey(key: string) {
  await navigator.clipboard.writeText(key)
  ElMessage.success('API 키가 복사되었습니다.')
}

// ── 감사 로그 (탭 D) ─────────────────────────
const logs = ref<AdminPage<ProcessLogResponse> | null>(null)
const logsLoading = ref(false)

const LOG_STATUS_MAP: Record<string, { label: string; type: 'success' | 'danger' | 'warning' | 'info' }> = {
  SUCCESS: { label: '성공', type: 'success' },
  FAILURE: { label: '실패', type: 'danger' },
  SKIP:    { label: '건너뜀', type: 'warning' },
}

async function loadLogs(page = 0) {
  logsLoading.value = true
  try {
    const res = await adminApi.getProcessLogs(page)
    logs.value = res.data.data
  } finally {
    logsLoading.value = false
  }
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
            <el-table-column label="상태" width="110">
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
            <el-table-column label="가입일" min-width="150">
              <template #default="{ row }">
                {{ new Date(row.createdAt).toLocaleString('ko-KR') }}
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
                  <el-button size="small" text @click="toggleKeyReveal(row.id)">
                    {{ revealedKeys.has(row.id) ? '숨김' : '보기' }}
                  </el-button>
                  <el-button size="small" text type="primary" @click="copyKey(row.apiKey)">
                    복사
                  </el-button>
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
            <el-table-column label="생성일" width="150">
              <template #default="{ row }">
                {{ new Date(row.createdAt).toLocaleString('ko-KR') }}
              </template>
            </el-table-column>
            <el-table-column label="작업" width="100">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  type="danger"
                  size="small"
                  plain
                  @click="handleDeactivateClient(row.id)"
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
            <el-table-column label="시각" width="155">
              <template #default="{ row }">
                {{ new Date(row.createdAt).toLocaleString('ko-KR') }}
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
</style>
