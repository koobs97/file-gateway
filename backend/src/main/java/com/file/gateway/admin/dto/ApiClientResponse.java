package com.file.gateway.admin.dto;

import com.file.gateway.client.entity.ApiClient;

import java.time.LocalDateTime;

public record ApiClientResponse(
        Long id,
        String clientName,
        String apiKey,
        String status,
        LocalDateTime createdAt
) {
    public static ApiClientResponse from(ApiClient client) {
        return new ApiClientResponse(
                client.getId(),
                client.getClientName(),
                client.getApiKey(),
                client.getStatus(),
                client.getCreatedAt()
        );
    }
}
