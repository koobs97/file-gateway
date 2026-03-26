package com.file.gateway.auth;

import com.file.gateway.auth.dto.LoginRequest;
import com.file.gateway.auth.dto.TokenResponse;
import com.file.gateway.common.config.JwtProperties;
import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.user.entity.User;
import com.file.gateway.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    private User activeUser;
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = new BCryptPasswordEncoder().encode(RAW_PASSWORD);

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .username("admin")
                .password(ENCODED_PASSWORD)
                .role("ROLE_ADMIN")
                .build();

        when(jwtTokenProvider.generateAccessToken(anyString())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(anyString())).thenReturn("refresh-token");
        when(jwtProperties.accessTokenExpiry()).thenReturn(900_000L);
    }

    // ─── login ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("올바른 자격증명으로 로그인 시 AT/RT를 반환한다")
    void login_ValidCredentials_ReturnsTokens() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        TokenResponse result = authService.login(new LoginRequest("admin", RAW_PASSWORD));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        verify(userRepository).findByUsername("admin");
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 로그인 시 INVALID_CREDENTIALS 예외를 던진다")
    void login_UnknownUser_ThrowsInvalidCredentials() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시 INVALID_CREDENTIALS 예외를 던진다")
    void login_WrongPassword_ThrowsInvalidCredentials() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", ENCODED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("비활성화된 계정으로 로그인 시 UNAUTHORIZED 예외를 던진다")
    void login_InactiveUser_ThrowsUnauthorized() {
        User inactiveUser = User.builder()
                .username("admin")
                .password(ENCODED_PASSWORD)
                .role("ROLE_ADMIN")
                .build();
        inactiveUser.deactivate();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    // ─── refresh ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 RT로 refresh 시 새 AT/RT를 반환한다")
    void refresh_ValidToken_ReturnsNewTokens() {
        activeUser.updateRefreshToken("valid-refresh-token");
        when(jwtTokenProvider.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("valid-refresh-token")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));

        TokenResponse result = authService.refresh("valid-refresh-token");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("유효하지 않은 RT로 refresh 시 INVALID_TOKEN 예외를 던진다")
    void refresh_InvalidToken_ThrowsInvalidToken() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("DB에 저장된 RT와 다른 값으로 refresh 시 INVALID_TOKEN 예외를 던진다")
    void refresh_TokenMismatch_ThrowsInvalidToken() {
        activeUser.updateRefreshToken("stored-refresh-token");
        when(jwtTokenProvider.validateToken("other-token")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("other-token")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.refresh("other-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    // ─── logout ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 시 DB의 refresh_token을 null로 초기화한다")
    void logout_ClearsRefreshToken() {
        activeUser.updateRefreshToken("some-refresh-token");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));

        authService.logout("admin");

        assertThat(activeUser.getRefreshToken()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 로그아웃해도 예외를 던지지 않는다")
    void logout_UnknownUser_DoesNotThrow() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // 예외 없이 실행되어야 함
        authService.logout("ghost");

        verify(userRepository).findByUsername("ghost");
    }
}
