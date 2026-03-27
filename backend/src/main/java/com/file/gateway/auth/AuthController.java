package com.file.gateway.auth;

import com.file.gateway.auth.dto.ChangePasswordRequest;
import com.file.gateway.auth.dto.LoginRequest;
import com.file.gateway.auth.dto.TokenResponse;
import com.file.gateway.auth.dto.UserInfoResponse;
import com.file.gateway.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController
 * - JWT 기반 인증 REST API 엔드포인트 제공
 * - 로그인, 토큰 갱신, 로그아웃, 내 정보 조회, 비밀번호 변경 기능을 처리
 *
 * @author 구본상
 * @since 2026-03-26
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 인증 비즈니스 로직을 처리하는 서비스 */
    private final AuthService authService;

    /**
     * 사용자 로그인 후 Access/Refresh 토큰을 발급
     *
     * @param request 사용자명과 비밀번호를 담은 로그인 요청 DTO (null 불허)
     * @return 발급된 Access Token, Refresh Token, 만료 시간을 담은 응답
     * @throws com.file.gateway.common.exception.BusinessException 자격 증명이 유효하지 않거나 계정이 비활성화된 경우
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse token = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(token));
    }

    /**
     * Refresh Token을 사용하여 새 Access Token과 Refresh Token을 재발급
     *
     * @param refreshToken 요청 헤더 "Refresh-Token"에 전달된 리프레시 토큰 (null 불허)
     * @return 새로 발급된 Access Token, Refresh Token, 만료 시간을 담은 응답
     * @throws com.file.gateway.common.exception.BusinessException 토큰이 유효하지 않거나 사용자를 찾을 수 없는 경우
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestHeader("Refresh-Token") String refreshToken) {
        TokenResponse token = authService.refresh(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok(token));
    }

    /**
     * 현재 인증된 사용자의 로그아웃을 처리하고 Refresh Token을 무효화
     *
     * @param principal Spring Security 컨텍스트에서 주입된 현재 사용자 정보 (null 불허)
     * @return 빈 성공 응답
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal.username());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 현재 인증된 사용자의 기본 정보를 반환
     *
     * @param principal Spring Security 컨텍스트에서 주입된 현재 사용자 정보 (null 불허)
     * @return 사용자 ID, 사용자명, 역할, 비밀번호 변경 여부를 담은 응답
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(UserInfoResponse.from(principal)));
    }

    /**
     * 현재 인증된 사용자의 비밀번호를 변경
     *
     * @param principal Spring Security 컨텍스트에서 주입된 현재 사용자 정보 (null 불허)
     * @param request   현재 비밀번호와 새 비밀번호를 담은 요청 DTO (null 불허)
     * @return 빈 성공 응답
     * @throws com.file.gateway.common.exception.BusinessException 현재 비밀번호가 일치하지 않는 경우
     */
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.username(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
