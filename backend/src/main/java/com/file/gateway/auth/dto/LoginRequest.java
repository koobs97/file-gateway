package com.file.gateway.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "사용자명을 입력하세요.")
        String username,

        @NotBlank(message = "비밀번호를 입력하세요.")
        String password
) {}
