package com.file.gateway.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * LoginRequest
 * - 로그인 API 요청 본문을 담는 DTO 레코드
 * - 사용자명과 비밀번호 모두 필수 값이며 공백 불허
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record LoginRequest(
        /** 로그인에 사용할 사용자명 (공백 불허) */
        @NotBlank(message = "사용자명을 입력하세요.")
        String username,

        /** 로그인에 사용할 비밀번호 (공백 불허) */
        @NotBlank(message = "비밀번호를 입력하세요.")
        String password
) {}
