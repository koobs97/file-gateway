package com.file.gateway.admin.dto;

import com.file.gateway.user.entity.User;

import java.time.LocalDateTime;

public record UserSummaryResponse(
        Long id,
        String username,
        String role,
        boolean active,
        LocalDateTime createdAt
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
