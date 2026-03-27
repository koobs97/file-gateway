package com.file.gateway.auth;

import com.file.gateway.client.entity.ApiClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * ApiClientPrincipal
 * - Spring Security의 UserDetails를 구현한 API 클라이언트 주체(Principal) 레코드
 * - ApiKeyAuthenticationFilter에서 API 키 인증 성공 시 SecurityContext에 저장
 * - ROLE_API_CLIENT 권한을 부여하며, 비밀번호는 사용하지 않음(null 반환)
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record ApiClientPrincipal(
        /** 인증된 API 클라이언트 엔티티 */
        ApiClient apiClient
) implements UserDetails {

    /**
     * ApiClient 엔티티를 ApiClientPrincipal 레코드로 변환
     *
     * @param apiClient 변환할 API 클라이언트 엔티티 (null 불허)
     * @return ApiClientPrincipal 인스턴스
     */
    public static ApiClientPrincipal from(ApiClient apiClient) {
        return new ApiClientPrincipal(apiClient);
    }

    /**
     * API 클라이언트에 부여된 권한 목록을 반환 (ROLE_API_CLIENT 고정)
     *
     * @return ROLE_API_CLIENT를 포함한 GrantedAuthority 목록
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"));
    }

    /**
     * API 클라이언트는 비밀번호 인증을 사용하지 않으므로 null을 반환
     *
     * @return 항상 null
     */
    @Override
    public String getPassword() {
        return null;
    }

    /**
     * API 클라이언트의 이름을 사용자명으로 반환
     *
     * @return API 클라이언트 이름 문자열
     */
    @Override
    public String getUsername() {
        return apiClient.getClientName();
    }

    /**
     * API 클라이언트의 활성화 여부를 반환
     *
     * @return 클라이언트 상태가 "ACTIVE"이면 true, 그 외에는 false
     */
    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(apiClient.getStatus());
    }
}
