package com.file.gateway.auth;

import com.file.gateway.client.entity.ApiClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record ApiClientPrincipal(ApiClient apiClient) implements UserDetails {

    public static ApiClientPrincipal from(ApiClient apiClient) {
        return new ApiClientPrincipal(apiClient);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return apiClient.getClientName();
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(apiClient.getStatus());
    }
}
