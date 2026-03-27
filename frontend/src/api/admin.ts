import client from './client'
import type { ApiResponse } from '../types/file'
import type { UserSummaryResponse, ApiClientResponse, ProcessLogResponse, AdminPage } from '../types/admin'
import type { FileEvent } from '../types/file'

export const adminApi = {
  // ── 사용자 관리 ──────────────────────────────
  createUser(username: string, password: string, role: string) {
    return client.post<ApiResponse<UserSummaryResponse>>('/admin/users', { username, password, role })
  },
  getUsers(page = 0) {
    return client.get<ApiResponse<AdminPage<UserSummaryResponse>>>('/admin/users', { params: { page, size: 20 } })
  },
  updateRole(id: number, role: string) {
    return client.patch<ApiResponse<UserSummaryResponse>>(`/admin/users/${id}/role`, { role })
  },
  updateStatus(id: number, active: boolean) {
    return client.patch<ApiResponse<UserSummaryResponse>>(`/admin/users/${id}/status`, { active })
  },

  // ── API 클라이언트 관리 ──────────────────────
  getApiClients(page = 0) {
    return client.get<ApiResponse<AdminPage<ApiClientResponse>>>('/admin/api-clients', { params: { page, size: 20 } })
  },
  createApiClient(clientName: string) {
    return client.post<ApiResponse<ApiClientResponse>>('/admin/api-clients', { clientName })
  },
  deactivateApiClient(id: number) {
    return client.delete<ApiResponse<void>>(`/admin/api-clients/${id}`)
  },

  // ── 감사 로그 ────────────────────────────────
  getProcessLogs(page = 0) {
    return client.get<ApiResponse<AdminPage<ProcessLogResponse>>>('/audit/files', { params: { page, size: 20 } })
  },
  getFileProcessLogs(fileId: number) {
    return client.get<ApiResponse<ProcessLogResponse[]>>(`/audit/files/${fileId}/logs`)
  },
  getFileEvents(fileId: number) {
    return client.get<ApiResponse<FileEvent[]>>(`/audit/files/${fileId}/events`)
  },

  // ── 통계 ─────────────────────────────────────
  getStatistics() {
    return client.get('/admin/statistics')
  },
}
