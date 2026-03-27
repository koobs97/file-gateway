package com.file.gateway.auth.dto;

/**
 * TokenResponse
 * - 로그인 및 토큰 갱신 API의 응답 본문을 담는 DTO 레코드
 * - Access Token, Refresh Token, 토큰 타입, 만료 시간(초)을 포함
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record TokenResponse(
        /** 인증에 사용되는 Access Token 문자열 */
        String accessToken,
        /** 토큰 갱신에 사용되는 Refresh Token 문자열 */
        String refreshToken,
        /** 토큰 타입 (항상 "Bearer") */
        String tokenType,
        /** Access Token 만료까지 남은 시간 (초 단위) */
        long expiresIn
) {
    /**
     * Access Token, Refresh Token, 만료 시간(초)을 받아 TokenResponse를 생성
     *
     * @param accessToken      발급된 Access Token 문자열 (null 불허)
     * @param refreshToken     발급된 Refresh Token 문자열 (null 불허)
     * @param expiresInSeconds Access Token 만료까지 남은 시간 (초)
     * @return 토큰 타입 "Bearer"가 포함된 TokenResponse 인스턴스
     */
    public static TokenResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
