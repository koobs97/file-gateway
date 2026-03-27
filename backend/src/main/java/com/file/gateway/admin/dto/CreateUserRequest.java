package com.file.gateway.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CreateUserRequest
 * - 관리자의 사용자 생성 API 요청 본문을 담는 DTO 레코드
 * - 사용자명, 초기 비밀번호, 역할을 필수 값으로 포함
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record CreateUserRequest(
        /** 생성할 사용자명 (공백 불허, 3~50자) */
        @NotBlank(message = "아이디를 입력하세요.")
        @Size(min = 3, max = 50, message = "아이디는 3~50자여야 합니다.")
        String username,

        /** 생성할 사용자의 초기 비밀번호 (공백 불허, 최소 8자 이상) */
        @NotBlank(message = "초기 비밀번호를 입력하세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        /** 부여할 역할 (공백 불허, 예: ROLE_ADMIN, ROLE_AUDITOR, ROLE_END_USER) */
        @NotBlank(message = "역할을 선택하세요.")
        String role
) {}
