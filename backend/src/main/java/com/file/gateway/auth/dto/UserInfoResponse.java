package com.file.gateway.auth.dto;

import com.file.gateway.auth.UserPrincipal;

public record UserInfoResponse(Long id, String username, String role) {

    public static UserInfoResponse from(UserPrincipal principal) {
        return new UserInfoResponse(principal.id(), principal.username(), principal.role());
    }
}
