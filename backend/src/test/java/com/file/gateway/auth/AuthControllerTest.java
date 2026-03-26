package com.file.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.file.gateway.auth.dto.LoginRequest;
import com.file.gateway.auth.dto.TokenResponse;
import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 슬라이스 테스트.
 * Spring Security를 제외하고 컨트롤러 로직(요청 매핑, 응답 포맷, 유효성 검사, 서비스 위임)만 검증한다.
 *
 * - logout / me 엔드포인트: @AuthenticationPrincipal이 Spring Security 아규먼트 리졸버를 필요로 하므로
 *   컨트롤러 슬라이스 테스트에서는 제외. 비즈니스 로직은 AuthServiceTest에서 충분히 커버함.
 * - 인증/인가 정책(401, 403): SecurityConfig 설정은 통합 테스트에서 검증한다.
 */
@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    // JwtAuthenticationFilter는 @Component Filter라 @WebMvcTest에서도 스캔됨 → 의존성 필요
    @MockitoBean
    @SuppressWarnings("unused")
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    @SuppressWarnings("unused")
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    @SuppressWarnings("unused")
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // ─── POST /api/v1/auth/login ──────────────────────────────────────────

    @Test
    @DisplayName("올바른 자격증명으로 로그인 시 200과 토큰 정보를 반환한다")
    void login_ValidCredentials_Returns200WithTokens() throws Exception {
        when(authService.login(any()))
                .thenReturn(TokenResponse.of("access-token", "refresh-token", 900L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    @DisplayName("잘못된 자격증명으로 로그인 시 401과 에러 코드를 반환한다")
    void login_InvalidCredentials_Returns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("username 미입력 시 400을 반환한다")
    void login_BlankUsername_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("password 미입력 시 400을 반환한다")
    void login_BlankPassword_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── POST /api/v1/auth/refresh ────────────────────────────────────────

    @Test
    @DisplayName("유효한 RT로 refresh 시 200과 새 AT/RT를 반환한다")
    void refresh_ValidToken_Returns200() throws Exception {
        when(authService.refresh(anyString()))
                .thenReturn(TokenResponse.of("new-access", "new-refresh", 900L));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Refresh-Token", "valid-refresh-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("유효하지 않은 RT로 refresh 시 401과 에러 코드를 반환한다")
    void refresh_InvalidToken_Returns401() throws Exception {
        when(authService.refresh(anyString()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Refresh-Token", "bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }
}
