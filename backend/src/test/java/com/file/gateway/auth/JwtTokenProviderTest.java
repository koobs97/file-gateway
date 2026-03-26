package com.file.gateway.auth;

import com.file.gateway.common.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                "test-jwt-secret-key-for-unit-test-must-be-32chars",
                900_000L,   // 15분
                604_800_000L // 7일
        );
        provider = new JwtTokenProvider(props);
    }

    @Test
    @DisplayName("generateAccessToken은 유효한 JWT를 생성한다")
    void generateAccessToken_ReturnsValidJwt() {
        String token = provider.generateAccessToken("admin");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    @DisplayName("getUsernameFromToken은 토큰에서 username을 추출한다")
    void getUsernameFromToken_ExtractsUsername() {
        String token = provider.generateAccessToken("admin");

        assertThat(provider.getUsernameFromToken(token)).isEqualTo("admin");
    }

    @Test
    @DisplayName("validateToken은 유효한 토큰에서 true를 반환한다")
    void validateToken_ValidToken_ReturnsTrue() {
        String token = provider.generateAccessToken("user1");

        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken은 만료된 토큰에서 false를 반환한다")
    void validateToken_ExpiredToken_ReturnsFalse() {
        JwtProperties expiredProps = new JwtProperties(
                "test-jwt-secret-key-for-unit-test-must-be-32chars",
                -1L, // 이미 만료
                604_800_000L
        );
        JwtTokenProvider expiredProvider = new JwtTokenProvider(expiredProps);
        String token = expiredProvider.generateAccessToken("user1");

        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken은 위조된 토큰에서 false를 반환한다")
    void validateToken_TamperedToken_ReturnsFalse() {
        String token = provider.generateAccessToken("user1");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("generateRefreshToken은 generateAccessToken과 다른 토큰을 생성한다")
    void generateRefreshToken_IsDifferentFromAccessToken() {
        String access = provider.generateAccessToken("admin");
        String refresh = provider.generateRefreshToken("admin");

        assertThat(access).isNotEqualTo(refresh);
    }

    @Test
    @DisplayName("refresh token에서도 username을 정상 추출한다")
    void getUsernameFromRefreshToken_ExtractsUsername() {
        String refresh = provider.generateRefreshToken("auditor");

        assertThat(provider.getUsernameFromToken(refresh)).isEqualTo("auditor");
    }
}
