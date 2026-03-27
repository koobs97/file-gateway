/**
 * 파일 관련 타입 정의
 * - 파일 업로드 응답·상세 정보·페이징·이벤트·통계·WebSocket 알림 인터페이스
 * - 공통 API 응답 래퍼 타입 포함
 */

/** 파일 업로드 응답 DTO */
export interface FileUploadResponse {
  /** 서버에서 발급된 파일 고유 식별자 */
  fileId: number
  /** 업로드 원본 파일명 */
  originalName: string
  /** 파일 크기 (바이트) */
  fileSize: number
  /** 업로드 직후 파일 처리 상태 (UPLOADED) */
  status: string
  /** 업로드 생성 시각 (ISO 8601) */
  createdAt: string
}

/** 파일 상세 정보 DTO */
export interface FileDetailResponse {
  /** 파일 고유 식별자 */
  id: number
  /** 업로드 원본 파일명 */
  originalName: string
  /** 서버 저장 파일명 (UUID 기반) */
  storedName: string
  /** 파일 크기 (바이트) */
  fileSize: number
  /** MIME 타입 */
  mimeType: string
  /** 파일 처리 상태 (UPLOADED | PROCESSING | DONE | FAIL) */
  status: string
  /** 업로드 생성 시각 (ISO 8601) */
  createdAt: string
  /** 마지막 상태 변경 시각 (ISO 8601) */
  updatedAt: string
}

/** 파일 목록 페이징 응답 */
export interface FilePage {
  /** 현재 페이지의 파일 상세 정보 목록 */
  content: FileDetailResponse[]
  /** 전체 파일 수 */
  totalElements: number
  /** 전체 페이지 수 */
  totalPages: number
  /** 현재 페이지 번호 (0-based) */
  number: number
  /** 페이지 크기 */
  size: number
}

/** 파일 이벤트 DTO */
export interface FileEvent {
  /** 이벤트 고유 식별자 */
  id: number
  /** 이벤트 유형 (예: FILE_UPLOADED, CDR_STARTED, CDR_DONE) */
  eventType: string
  /** 이벤트 추가 데이터 (JSON 직렬화 문자열 또는 null) */
  payload: string | null
  /** 이벤트 발생 시각 (ISO 8601) */
  createdAt: string
}

/** 파일 처리 통계 DTO */
export interface FileStatistics {
  /** 전체 파일 수 */
  total: number
  /** 처리 완료 (DONE) 파일 수 */
  done: number
  /** 처리 실패 (FAIL) 파일 수 */
  fail: number
  /** 처리 중 (PROCESSING) 파일 수 */
  processing: number
  /** 삭제된 파일 수 */
  deleted: number
  /** CDR 성공률 (0.0 ~ 100.0) */
  successRate: number
}

/** WebSocket을 통해 수신되는 파일 상태 변경 알림 */
export interface FileStatusNotification {
  /** 상태가 변경된 파일 ID */
  fileId: number
  /** 새로운 파일 처리 상태 */
  status: string
  /** 상태 변경 발생 시각 (ISO 8601) */
  timestamp: string
}

/** 공통 API 응답 래퍼 */
export interface ApiResponse<T> {
  /** 요청 성공 여부 */
  success: boolean
  /** 응답 데이터 페이로드 */
  data: T
  /** 오류 정보 (성공 시 null) */
  error: { code: string; message: string } | null
}
