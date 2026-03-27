package com.file.gateway.admin.dto;

import com.file.gateway.client.entity.ApiClient;

import java.time.LocalDateTime;

/**
 * ApiClientResponse
 * - API 클라이언트 관리 API의 응답 본문을 담는 DTO 레코드
 * - 클라이언트 ID, 이름, API 키, 상태, 생성일시를 포함
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record ApiClientResponse(
        /** API 클라이언트 고유 식별자 */
        Long id,
        /** API 클라이언트 이름 */
        String clientName,
        /** 인증에 사용되는 API 키 (UUID 형식) */
        String apiKey,
        /** 클라이언트 상태 (ACTIVE 또는 INACTIVE) */
        String status,
        /** API 클라이언트 생성일시 */
        LocalDateTime createdAt
) {
    /**
     * ApiClient 엔티티를 ApiClientResponse로 변환
     *
     * @param client 변환할 API 클라이언트 엔티티 (null 불허)
     * @return API 클라이언트 정보를 담은 ApiClientResponse 인스턴스
     */
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
