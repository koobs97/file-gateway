/**
 * 관리자 API 모듈
 * - 사용자 관리: 생성·목록 조회·역할 변경·활성화 상태 변경
 * - API 클라이언트 관리: 목록 조회·생성·비활성화
 * - 감사 로그: 처리 로그·파일별 로그·파일별 이벤트 조회
 * - 통계: 파일 처리 통계 조회
 * - 모든 엔드포인트는 ROLE_ADMIN 이상의 권한이 필요
 */
import client from './client'
import type { ApiResponse } from '../types/file'
import type { UserSummaryResponse, ApiClientResponse, ProcessLogResponse, AdminPage } from '../types/admin'
import type { FileEvent } from '../types/file'

export const adminApi = {
  // ── 사용자 관리 ──────────────────────────────
  /**
   * 새 사용자 생성
   *
   * @param username 로그인 아이디
   * @param password 초기 비밀번호
   * @param role 부여할 역할 (ROLE_ADMIN | ROLE_AUDITOR | ROLE_END_USER)
   * @returns 생성된 사용자 요약 정보
   */
  createUser(username: string, password: string, role: string) {
    return client.post<ApiResponse<UserSummaryResponse>>('/admin/users', { username, password, role })
  },

  /**
   * 사용자 목록 조회 (페이징)
   *
   * @param page 페이지 번호 (0-based, 기본값 0)
   * @returns 페이징된 사용자 목록
   */
  getUsers(page = 0) {
    return client.get<ApiResponse<AdminPage<UserSummaryResponse>>>('/admin/users', { params: { page, size: 20 } })
  },

  /**
   * 사용자 역할 변경
   *
   * @param id 대상 사용자 ID
   * @param role 변경할 역할
   * @returns 변경된 사용자 요약 정보
   */
  updateRole(id: number, role: string) {
    return client.patch<ApiResponse<UserSummaryResponse>>(`/admin/users/${id}/role`, { role })
  },

  /**
   * 사용자 활성화·비활성화 상태 변경
   *
   * @param id 대상 사용자 ID
   * @param active true이면 활성화, false이면 비활성화
   * @returns 변경된 사용자 요약 정보
   */
  updateStatus(id: number, active: boolean) {
    return client.patch<ApiResponse<UserSummaryResponse>>(`/admin/users/${id}/status`, { active })
  },

  // ── API 클라이언트 관리 ──────────────────────
  /**
   * API 클라이언트 목록 조회 (페이징)
   *
   * @param page 페이지 번호 (0-based, 기본값 0)
   * @returns 페이징된 API 클라이언트 목록
   */
  getApiClients(page = 0) {
    return client.get<ApiResponse<AdminPage<ApiClientResponse>>>('/admin/api-clients', { params: { page, size: 20 } })
  },

  /**
   * API 클라이언트 생성 및 API 키 발급
   *
   * @param clientName 클라이언트 식별 이름
   * @returns 생성된 클라이언트 정보 (apiKey 포함, 발급 직후에만 확인 가능)
   */
  createApiClient(clientName: string) {
    return client.post<ApiResponse<ApiClientResponse>>('/admin/api-clients', { clientName })
  },

  /**
   * API 클라이언트 비활성화
   *
   * @param id 비활성화할 클라이언트 ID
   * @returns 빈 응답
   */
  deactivateApiClient(id: number) {
    return client.delete<ApiResponse<void>>(`/admin/api-clients/${id}`)
  },

  // ── 감사 로그 ────────────────────────────────
  /**
   * 전체 파일 처리 로그 조회 (페이징)
   *
   * @param page 페이지 번호 (0-based, 기본값 0)
   * @returns 페이징된 처리 로그 목록
   */
  getProcessLogs(page = 0) {
    return client.get<ApiResponse<AdminPage<ProcessLogResponse>>>('/audit/files', { params: { page, size: 20 } })
  },

  /**
   * 특정 파일의 처리 단계별 로그 조회
   *
   * @param fileId 조회할 파일 ID
   * @returns 해당 파일의 처리 로그 목록
   */
  getFileProcessLogs(fileId: number) {
    return client.get<ApiResponse<ProcessLogResponse[]>>(`/audit/files/${fileId}/logs`)
  },

  /**
   * 특정 파일의 이벤트 이력 조회
   *
   * @param fileId 조회할 파일 ID
   * @returns 해당 파일에 발생한 이벤트 목록
   */
  getFileEvents(fileId: number) {
    return client.get<ApiResponse<FileEvent[]>>(`/audit/files/${fileId}/events`)
  },

  // ── 통계 ─────────────────────────────────────
  /**
   * 파일 처리 통계 조회
   *
   * @returns 전체·완료·실패·처리중·삭제 건수 및 성공률
   */
  getStatistics() {
    return client.get('/admin/statistics')
  },
}
