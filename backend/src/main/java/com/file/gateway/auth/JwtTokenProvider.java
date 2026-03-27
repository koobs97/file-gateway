package com.file.gateway.auth;

import com.file.gateway.common.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtTokenProvider
 * - HMAC-SHA 알고리즘을 사용한 JWT 토큰 생성, 파싱, 검증 담당
 * - Access Token과 Refresh Token의 만료 시간을 각각 독립적으로 관리
 * - 서명 키는 JwtProperties의 secret 값을 UTF-8 바이트로 변환하여 생성
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** JWT 서명 및 검증에 사용되는 HMAC 비밀 키 */
    private final SecretKey signingKey;

    /** Access Token 만료 시간 (밀리초) */
    private final long accessTokenExpiry;

    /** Refresh Token 만료 시간 (밀리초) */
    private final long refreshTokenExpiry;

    /**
     * JwtProperties 설정값을 기반으로 서명 키와 만료 시간을 초기화
     *
     * @param jwtProperties JWT 관련 설정(secret, 만료 시간)을 담은 프로퍼티 (null 불허)
     */
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = jwtProperties.accessTokenExpiry();
        this.refreshTokenExpiry = jwtProperties.refreshTokenExpiry();
    }

    /**
     * 사용자명을 subject로 포함한 Access Token을 생성
     *
     * @param username 토큰에 저장할 사용자명 (null 불허)
     * @return 서명된 Access Token 문자열
     */
    public String generateAccessToken(String username) {
        return buildToken(username, accessTokenExpiry);
    }

    /**
     * 사용자명을 subject로 포함한 Refresh Token을 생성
     *
     * @param username 토큰에 저장할 사용자명 (null 불허)
     * @return 서명된 Refresh Token 문자열
     */
    public String generateRefreshToken(String username) {
        return buildToken(username, refreshTokenExpiry);
    }

    /**
     * JWT 토큰에서 사용자명(subject)을 추출
     *
     * @param token 파싱할 JWT 토큰 문자열 (null 불허)
     * @return 토큰의 subject 클레임에 저장된 사용자명
     * @throws JwtException 토큰이 유효하지 않거나 만료된 경우
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * JWT 토큰의 서명 및 만료 여부를 검증
     *
     * @param token 검증할 JWT 토큰 문자열 (null 불허)
     * @return 토큰이 유효하면 true, 만료되었거나 서명이 올바르지 않으면 false
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("만료된 JWT 토큰: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 JWT 토큰: {}", e.getMessage());
        }
        return false;
    }

    /**
     * subject와 만료 시간을 설정하여 서명된 JWT 토큰을 생성
     *
     * @param username 토큰의 subject로 설정할 사용자명 (null 불허)
     * @param expiry   토큰 유효 기간 (밀리초)
     * @return 서명된 JWT 토큰 문자열
     */
    private String buildToken(String username, long expiry) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * JWT 토큰을 파싱하여 Claims(페이로드)를 반환
     *
     * @param token 파싱할 JWT 토큰 문자열 (null 불허)
     * @return 파싱된 Claims 객체
     * @throws JwtException 서명 불일치, 만료, 형식 오류 등 토큰이 유효하지 않은 경우
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
