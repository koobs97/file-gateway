package com.file.gateway.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "아이디를 입력하세요.")
        @Size(min = 3, max = 50, message = "아이디는 3~50자여야 합니다.")
        String username,

        @NotBlank(message = "초기 비밀번호를 입력하세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "역할을 선택하세요.")
        String role
) {}
