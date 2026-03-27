package com.file.gateway.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ChangePasswordRequest
 * - 비밀번호 변경 API 요청 본문을 담는 DTO 레코드
 * - 현재 비밀번호 검증 후 새 비밀번호로 변경하는 데 사용
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record ChangePasswordRequest(
        /** 본인 확인을 위한 현재 비밀번호 (공백 불허) */
        @NotBlank(message = "현재 비밀번호를 입력하세요.")
        String currentPassword,

        /** 변경할 새 비밀번호 (공백 불허, 최소 8자 이상) */
        @NotBlank(message = "새 비밀번호를 입력하세요.")
        @Size(min = 8, message = "새 비밀번호는 8자 이상이어야 합니다.")
        String newPassword
) {}
