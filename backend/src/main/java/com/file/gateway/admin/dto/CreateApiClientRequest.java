package com.file.gateway.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * CreateApiClientRequest
 * - API 클라이언트 생성 요청 본문을 담는 DTO 레코드
 * - 클라이언트 이름은 필수 값이며, API 키는 서버에서 자동 생성됨
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record CreateApiClientRequest(
        /** 생성할 API 클라이언트의 이름 (공백 불허) */
        @NotBlank(message = "클라이언트 이름은 필수입니다.")
        String clientName
) {
}
