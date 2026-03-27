package com.file.gateway.auth;

import com.file.gateway.auth.dto.ChangePasswordRequest;
import com.file.gateway.auth.dto.LoginRequest;
import com.file.gateway.auth.dto.TokenResponse;
import com.file.gateway.common.config.JwtProperties;
import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.user.entity.User;
import com.file.gateway.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService
 * - JWT 기반 인증 핵심 비즈니스 로직 처리
 * - 로그인 시 Access/Refresh Token 발급 및 DB 저장
 * - Refresh Token 검증을 통한 토큰 재발급
 * - 로그아웃 시 Refresh Token 무효화
 * - 비밀번호 변경 처리
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 사용자 엔티티 조회 및 저장을 위한 Repository */
    private final UserRepository userRepository;

    /** 비밀번호 암호화 및 검증을 위한 인코더 */
    private final PasswordEncoder passwordEncoder;

    /** JWT 토큰 생성 및 검증 컴포넌트 */
    private final JwtTokenProvider jwtTokenProvider;

    /** Access/Refresh Token 만료 시간 등 JWT 설정 프로퍼티 */
    private final JwtProperties jwtProperties;

    /**
     * 사용자 자격 증명을 검증하고 Access Token과 Refresh Token을 발급
     *
     * @param request 사용자명과 비밀번호를 담은 로그인 요청 DTO (null 불허)
     * @return 발급된 Access Token, Refresh Token, 토큰 타입, 만료 시간(초) 응답
     * @throws BusinessException 사용자명 또는 비밀번호가 일치하지 않는 경우 (INVALID_CREDENTIALS)
     * @throws BusinessException 계정이 비활성화 상태인 경우 (UNAUTHORIZED)
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비활성화된 계정입니다.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        // 새 Refresh Token을 DB에 저장하여 이후 갱신 요청 시 검증에 사용
        user.updateRefreshToken(refreshToken);

        return TokenResponse.of(accessToken, refreshToken, jwtProperties.accessTokenExpiry() / 1000);
    }

    /**
     * Refresh Token의 유효성을 검증하고 새 Access Token과 Refresh Token을 재발급 (Refresh Token Rotation)
     *
     * @param refreshToken 클라이언트가 전달한 리프레시 토큰 (null 불허)
     * @return 새로 발급된 Access Token, Refresh Token, 토큰 타입, 만료 시간(초) 응답
     * @throws BusinessException 토큰 서명·만료 등 유효성 검증 실패 시 (INVALID_TOKEN)
     * @throws BusinessException DB에 저장된 Refresh Token과 불일치하는 경우 (INVALID_TOKEN)
     * @throws BusinessException 해당 사용자를 찾을 수 없는 경우 (USER_NOT_FOUND)
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        /* DB에 저장된 Refresh Token과 요청 토큰을 대조하여
           탈취된 구 토큰으로의 재사용 공격을 방지 */
        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(username);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);
        user.updateRefreshToken(newRefreshToken);

        return TokenResponse.of(newAccessToken, newRefreshToken, jwtProperties.accessTokenExpiry() / 1000);
    }

    /**
     * 사용자의 Refresh Token을 null로 초기화하여 로그아웃 처리
     *
     * @param username 로그아웃할 사용자의 사용자명 (null 불허)
     */
    @Transactional
    public void logout(String username) {
        userRepository.findByUsername(username)
                .ifPresent(user -> user.updateRefreshToken(null));
    }

    /**
     * 현재 비밀번호를 검증한 후 새 비밀번호로 변경
     *
     * @param username 비밀번호를 변경할 사용자의 사용자명 (null 불허)
     * @param request  현재 비밀번호와 새 비밀번호를 담은 요청 DTO (null 불허)
     * @throws BusinessException 해당 사용자를 찾을 수 없는 경우 (USER_NOT_FOUND)
     * @throws BusinessException 현재 비밀번호가 일치하지 않는 경우 (INVALID_CURRENT_PASSWORD)
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        user.markPasswordChanged(passwordEncoder.encode(request.newPassword()));
    }
}
