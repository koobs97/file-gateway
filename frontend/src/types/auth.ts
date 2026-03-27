/**
 * 인증 관련 타입 정의
 * - 로그인 요청·토큰 응답·사용자 정보 인터페이스
 */

/** 로그인 요청 DTO */
export interface LoginRequest {
  /** 로그인 아이디 */
  username: string
  /** 비밀번호 */
  password: string
}

/** JWT 토큰 발급 응답 DTO */
export interface TokenResponse {
  /** 발급된 액세스 토큰 */
  accessToken: string
  /** 발급된 리프레시 토큰 */
  refreshToken: string
  /** 토큰 타입 (Bearer) */
  tokenType: string
  /** 액세스 토큰 만료까지 남은 시간 (초) */
  expiresIn: number
}

/** 인증된 사용자 정보 DTO */
export interface UserInfo {
  /** 사용자 고유 식별자 */
  id: number
  /** 로그인 아이디 */
  username: string
  /** 부여된 역할 (ROLE_ADMIN | ROLE_AUDITOR | ROLE_END_USER | ROLE_API_CLIENT) */
  role: string
  /** 비밀번호 변경 완료 여부 (false이면 최초 로그인으로 간주하여 변경 강제) */
  passwordChanged: boolean
}
