/**
 * 파일 API 모듈
 * - 파일 업로드·목록 조회·상세 조회·이벤트 조회·삭제·다운로드·통계 엔드포인트 호출
 * - multipart/form-data 업로드 진행률 콜백 지원
 */
import client from './client'
import type { ApiResponse, FileDetailResponse, FileEvent, FilePage, FileStatistics, FileUploadResponse } from '../types/file'

export const fileApi = {
  /**
   * 파일 업로드 (CDR 처리 시작)
   *
   * @param file 업로드할 File 객체
   * @param onProgress 업로드 진행률 콜백 (0~100)
   * @returns 업로드된 파일 정보 (fileId, originalName, status 등)
   */
  upload(file: File, onProgress?: (percent: number) => void) {
    const form = new FormData()
    form.append('file', file)
    return client.post<ApiResponse<FileUploadResponse>>('/files', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        if (onProgress && e.total) onProgress(Math.round((e.loaded * 100) / e.total))
      },
    })
  },

  /**
   * 파일 목록 조회 (페이징·검색·상태 필터 지원)
   *
   * @param page 페이지 번호 (0-based, 기본값 0)
   * @param size 페이지 크기 (기본값 20)
   * @param keyword 파일명 검색어 (선택)
   * @param status 파일 상태 필터 (선택, UPLOADED | PROCESSING | DONE | FAIL)
   * @returns 페이징된 파일 목록
   */
  getList(page = 0, size = 20, keyword?: string, status?: string) {
    return client.get<ApiResponse<FilePage>>('/files', {
      params: { page, size, keyword: keyword || undefined, status: status || undefined },
    })
  },

  /**
   * 파일 상세 정보 조회
   *
   * @param id 파일 고유 식별자
   * @returns 파일 상세 DTO (storedName, mimeType, 타임스탬프 포함)
   */
  getDetail(id: number) {
    return client.get<ApiResponse<FileDetailResponse>>(`/files/${id}`)
  },

  /**
   * 파일 이벤트 이력 조회
   *
   * @param id 파일 고유 식별자
   * @returns 해당 파일에 발생한 이벤트 목록
   */
  getEvents(id: number) {
    return client.get<ApiResponse<FileEvent[]>>(`/files/${id}/events`)
  },

  /**
   * 파일 삭제 (소프트 딜리트)
   *
   * @param id 삭제할 파일 고유 식별자
   * @returns 빈 응답
   */
  delete(id: number) {
    return client.delete<ApiResponse<void>>(`/files/${id}`)
  },

  /**
   * 무해화된 파일 다운로드
   *
   * @param id 다운로드할 파일 고유 식별자
   * @returns Blob 형태의 파일 데이터
   */
  downloadFile(id: number) {
    return client.get<Blob>(`/files/${id}/download`, { responseType: 'blob' })
  },

  /**
   * 파일 처리 통계 조회
   *
   * @returns 전체·완료·실패·처리중·삭제 건수 및 성공률
   */
  getStatistics() {
    return client.get<ApiResponse<FileStatistics>>('/admin/statistics')
  },
}
