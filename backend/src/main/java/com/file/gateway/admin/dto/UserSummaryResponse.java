package com.file.gateway.admin.dto;

import com.file.gateway.user.entity.User;

import java.time.LocalDateTime;

/**
 * UserSummaryResponse
 * - 사용자 관리 API의 응답 본문을 담는 DTO 레코드
 * - 사용자 ID, 사용자명, 역할, 활성화 여부, 생성일시를 포함
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record UserSummaryResponse(
        /** 사용자 고유 식별자 */
        Long id,
        /** 사용자명 */
        String username,
        /** 사용자 역할 (예: ROLE_ADMIN, ROLE_AUDITOR, ROLE_END_USER) */
        String role,
        /** 계정 활성화 여부 */
        boolean active,
        /** 계정 생성일시 */
        LocalDateTime createdAt
) {
    /**
     * User 엔티티를 UserSummaryResponse로 변환
     *
     * @param user 변환할 사용자 엔티티 (null 불허)
     * @return 사용자 요약 정보를 담은 UserSummaryResponse 인스턴스
     */
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
