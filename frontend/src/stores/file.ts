/**
 * 파일 목록 Pinia 스토어
 * - 파일 목록 페이지 데이터와 로딩 상태를 관리
 * - 목록 조회 액션 및 WebSocket 수신 시 파일 상태 실시간 갱신 지원
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fileApi } from '../api/files'
import type { FilePage } from '../types/file'

export const useFileStore = defineStore('file', () => {
  // ── 상태 정의 ─────────────────────────────────
  /** 현재 페이지의 파일 목록 데이터 (null이면 미로드) */
  const page = ref<FilePage | null>(null)
  /** 목록 조회 로딩 여부 */
  const loading = ref(false)

  // ── API 액션 ───────────────────────────────────
  /**
   * 파일 목록을 서버에서 조회하여 상태에 반영
   *
   * @param pageNum 조회할 페이지 번호 (0-based, 기본값 0)
   * @param keyword 파일명 검색어 (선택)
   * @param status 파일 상태 필터 (선택)
   */
  async function fetchList(pageNum = 0, keyword?: string, status?: string) {
    loading.value = true
    try {
      const res = await fileApi.getList(pageNum, 20, keyword, status)
      page.value = res.data.data
    } finally {
      loading.value = false
    }
  }

  /**
   * 특정 파일의 상태를 현재 페이지 데이터에서 직접 갱신 (WebSocket 알림 수신 시 사용)
   *
   * @param fileId 상태를 변경할 파일 ID
   * @param status 새로운 파일 상태 (UPLOADED | PROCESSING | DONE | FAIL)
   */
  function updateStatus(fileId: number, status: string) {
    if (!page.value) return
    const item = page.value.content.find((f) => f.id === fileId)
    if (item) item.status = status
  }

  return { page, loading, fetchList, updateStatus }
})
