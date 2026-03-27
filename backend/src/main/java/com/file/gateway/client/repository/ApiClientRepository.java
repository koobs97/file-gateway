package com.file.gateway.client.repository;

import com.file.gateway.client.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ApiClientRepository
 * - ApiClient 엔티티에 대한 데이터 접근 계층이다.
 * - API 키 기반 클라이언트 조회 메서드를 제공한다.
 * - ApiKeyAuthenticationFilter에서 요청 헤더의 X-API-Key로 클라이언트를 검증할 때 사용된다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public interface ApiClientRepository extends JpaRepository<ApiClient, Long> {

    /**
     * API 키로 ApiClient를 조회한다.
     *
     * @param apiKey 조회할 API 키 문자열
     * @return 해당 API 키의 ApiClient Optional
     */
    Optional<ApiClient> findByApiKey(String apiKey);
}
