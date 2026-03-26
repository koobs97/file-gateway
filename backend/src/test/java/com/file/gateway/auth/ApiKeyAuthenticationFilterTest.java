package com.file.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.file.gateway.client.entity.ApiClient;
import com.file.gateway.client.repository.ApiClientRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiClientRepository apiClientRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ApiKeyAuthenticationFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 API 키가 있으면 인증이 설정된다")
    void validApiKey_SetsAuthentication() throws Exception {
        ApiClient client = ApiClient.builder()
                .clientName("test-client")
                .apiKey("valid-key")
                .status("ACTIVE")
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiClientRepository.findByApiKey("valid-key")).thenReturn(Optional.of(client));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_API_CLIENT"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("X-API-Key 헤더가 없으면 pass-through 된다")
    void noApiKeyHeader_PassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(apiClientRepository, never()).findByApiKey(any());
    }

    @Test
    @DisplayName("존재하지 않는 API 키는 401을 반환한다")
    void unknownApiKey_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "unknown-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiClientRepository.findByApiKey("unknown-key")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("비활성 클라이언트 API 키는 401을 반환한다")
    void inactiveClient_Returns401() throws Exception {
        ApiClient client = ApiClient.builder()
                .clientName("inactive-client")
                .apiKey("inactive-key")
                .status("INACTIVE")
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "inactive-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiClientRepository.findByApiKey("inactive-key")).thenReturn(Optional.of(client));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }
}
