package com.file.gateway.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateApiClientRequest(
        @NotBlank(message = "클라이언트 이름은 필수입니다.")
        String clientName
) {
}
