package com.file.gateway.client.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * ApiClient
 * - X-API-Key 헤더 기반 인증을 사용하는 외부 API 클라이언트 정보를 저장하는 JPA 엔티티이다.
 * - 클라이언트명, 고유 API 키, 활성화 상태(ACTIVE/INACTIVE)를 관리한다.
 * - ApiKeyAuthenticationFilter에서 요청 헤더의 API 키와 이 엔티티를 비교하여 인증을 처리한다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Entity
@Table(name = "api_client")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiClient {

    /** API 클라이언트 고유 식별자 (자동 증가) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** API 클라이언트 이름 (식별용) */
    @Column(name = "client_name", nullable = false)
    private String clientName;

    /** X-API-Key 헤더 인증에 사용되는 고유 키 값 */
    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    /** 클라이언트 활성화 상태 (ACTIVE 또는 INACTIVE) */
    @Column(name = "status", nullable = false)
    private String status;

    /** 레코드 생성 시각 (자동 설정, 변경 불가) */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * ApiClient 빌더 생성자.
     * status가 null이면 ACTIVE로 기본값을 설정한다.
     *
     * @param clientName 클라이언트 이름
     * @param apiKey     API 키 문자열
     * @param status     활성화 상태 (null이면 ACTIVE)
     */
    @Builder
    public ApiClient(String clientName, String apiKey, String status) {
        this.clientName = clientName;
        this.apiKey = apiKey;
        this.status = status != null ? status : "ACTIVE";
    }

    /**
     * API 클라이언트를 비활성화하여 해당 키로의 인증을 차단한다.
     */
    public void deactivate() {
        this.status = "INACTIVE";
    }
}
