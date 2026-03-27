package com.file.gateway.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JwtProperties
 * - application.yml의 app.jwt 프리픽스 설정값을 바인딩하는 불변 레코드
 * - JWT 서명 비밀키, 액세스 토큰 만료 시간, 리프레시 토큰 만료 시간을 보유
 *
 * @author 구본상
 * @since 2026-03-26
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        /** JWT 서명에 사용하는 비밀키 (32자 이상 필수) */
        String secret,

        /** 액세스 토큰 만료 시간 (밀리초) */
        long accessTokenExpiry,

        /** 리프레시 토큰 만료 시간 (밀리초) */
        long refreshTokenExpiry
) {}
