import client from './client'
import type { LoginRequest, TokenResponse, UserInfo } from '../types/auth'

interface ApiResponse<T> {
  success: boolean
  data: T
}

export const authApi = {
  login: (data: LoginRequest) =>
    client.post<ApiResponse<TokenResponse>>('/auth/login', data),

  logout: () =>
    client.post('/auth/logout'),

  refresh: (refreshToken: string) =>
    client.post<ApiResponse<TokenResponse>>('/auth/refresh', null, {
      headers: { 'Refresh-Token': refreshToken },
    }),

  me: () =>
    client.get<ApiResponse<UserInfo>>('/auth/me'),
}
