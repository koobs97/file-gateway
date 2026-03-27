package com.file.gateway.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * UpdateRoleRequest
 * - 사용자 역할 변경 API 요청 본문을 담는 DTO 레코드
 * - 역할 값은 ROLE_ADMIN, ROLE_AUDITOR, ROLE_END_USER 중 하나여야 함
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record UpdateRoleRequest(
        /** 변경할 역할 문자열 (공백 불허, 허용값: ROLE_ADMIN | ROLE_AUDITOR | ROLE_END_USER) */
        @NotBlank(message = "역할은 필수입니다.")
        @Pattern(regexp = "ROLE_ADMIN|ROLE_AUDITOR|ROLE_END_USER", message = "유효하지 않은 역할입니다.")
        String role
) {
}
