package com.file.gateway.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateRoleRequest(
        @NotBlank(message = "역할은 필수입니다.")
        @Pattern(regexp = "ROLE_ADMIN|ROLE_AUDITOR|ROLE_END_USER", message = "유효하지 않은 역할입니다.")
        String role
) {
}
