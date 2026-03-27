package com.file.gateway.auth.dto;

import com.file.gateway.auth.UserPrincipal;

public record UserInfoResponse(Long id, String username, String role, boolean passwordChanged) {

    public static UserInfoResponse from(UserPrincipal principal) {
        return new UserInfoResponse(principal.id(), principal.username(), principal.role(), principal.passwordChanged());
    }
}
