package com.file.gateway.auth.dto;

import com.file.gateway.auth.UserPrincipal;

/**
 * UserInfoResponse
 * - 현재 로그인한 사용자의 기본 정보를 반환하는 DTO 레코드
 * - "/api/v1/auth/me" 엔드포인트의 응답 본문으로 사용
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record UserInfoResponse(
        /** 사용자 고유 식별자 */
        Long id,
        /** 사용자명 */
        String username,
        /** 사용자 역할 (예: ROLE_ADMIN, ROLE_END_USER) */
        String role,
        /** 최초 로그인 이후 비밀번호 변경 완료 여부 */
        boolean passwordChanged
) {

    /**
     * UserPrincipal에서 UserInfoResponse를 생성
     *
     * @param principal SecurityContext에 저장된 현재 사용자 주체 (null 불허)
     * @return 사용자 ID, 사용자명, 역할, 비밀번호 변경 여부를 담은 UserInfoResponse
     */
    public static UserInfoResponse from(UserPrincipal principal) {
        return new UserInfoResponse(principal.id(), principal.username(), principal.role(), principal.passwordChanged());
    }
}
