/**
 * 관리자 기능 관련 타입 정의
 * - 사용자 요약·API 클라이언트·처리 로그 응답 인터페이스
 * - 관리자 전용 페이징 래퍼 타입 포함
 */

/** 사용자 요약 응답 DTO */
export interface UserSummaryResponse {
  /** 사용자 고유 식별자 */
  id: number
  /** 로그인 아이디 */
  username: string
  /** 부여된 역할 (ROLE_ADMIN | ROLE_AUDITOR | ROLE_END_USER) */
  role: string
  /** 계정 활성화 여부 */
  active: boolean
  /** 계정 생성 시각 (ISO 8601) */
  createdAt: string
}

/** API 클라이언트 응답 DTO */
export interface ApiClientResponse {
  /** API 클라이언트 고유 식별자 */
  id: number
  /** 클라이언트 식별 이름 */
  clientName: string
  /** 발급된 API 키 (생성 직후에만 전체 값 반환) */
  apiKey: string
  /** 클라이언트 상태 (ACTIVE | INACTIVE) */
  status: string
  /** 클라이언트 생성 시각 (ISO 8601) */
  createdAt: string
}

/** 파일 처리 단계 로그 응답 DTO */
export interface ProcessLogResponse {
  /** 로그 고유 식별자 */
  id: number
  /** 로그가 속한 파일 ID */
  fileId: number
  /** 처리 단계명 (예: UPLOAD, CDR_ANALYZE, CDR_SANITIZE) */
  step: string
  /** 처리 결과 상태 (SUCCESS | FAIL) */
  status: string
  /** 처리 결과 상세 메시지 */
  message: string
  /** 로그 생성 시각 (ISO 8601) */
  createdAt: string
}

/**
 * 관리자 전용 페이징 응답 래퍼
 *
 * @template T 페이지 내 항목 타입
 */
export interface AdminPage<T> {
  /** 현재 페이지의 항목 목록 */
  content: T[]
  /** 전체 항목 수 */
  totalElements: number
  /** 전체 페이지 수 */
  totalPages: number
  /** 현재 페이지 번호 (0-based) */
  number: number
  /** 페이지 크기 */
  size: number
}
