/**
 * 인증 API 모듈
 * - 로그인·로그아웃·토큰 리프레시·내 정보 조회·비밀번호 변경 엔드포인트 호출
 * - 공통 Axios 클라이언트를 사용하여 Authorization 헤더 및 인터셉터를 공유
 */
import client from './client'
import type { LoginRequest, TokenResponse, UserInfo } from '../types/auth'

/** 공통 API 응답 래퍼 */
interface ApiResponse<T> {
  success: boolean
  data: T
}

export const authApi = {
  /**
   * 사용자 로그인
   *
   * @param data 로그인 요청 DTO (username, password)
   * @returns 액세스 토큰·리프레시 토큰을 포함한 TokenResponse
   */
  login: (data: LoginRequest) =>
    client.post<ApiResponse<TokenResponse>>('/auth/login', data),

  /**
   * 로그아웃 (서버 측 토큰 무효화)
   *
   * @returns 빈 응답 (204)
   */
  logout: () =>
    client.post('/auth/logout'),

  /**
   * 액세스 토큰 재발급
   *
   * @param refreshToken 현재 유효한 리프레시 토큰
   * @returns 새로운 액세스 토큰·리프레시 토큰
   */
  refresh: (refreshToken: string) =>
    client.post<ApiResponse<TokenResponse>>('/auth/refresh', null, {
      headers: { 'Refresh-Token': refreshToken },
    }),

  /**
   * 현재 인증된 사용자 정보 조회
   *
   * @returns 사용자 ID·username·role·passwordChanged 포함 UserInfo
   */
  me: () =>
    client.get<ApiResponse<UserInfo>>('/auth/me'),

  /**
   * 비밀번호 변경
   *
   * @param currentPassword 현재 비밀번호
   * @param newPassword 변경할 새 비밀번호
   * @returns 빈 응답
   */
  changePassword: (currentPassword: string, newPassword: string) =>
    client.patch('/auth/password', { currentPassword, newPassword }),
}
