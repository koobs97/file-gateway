package com.file.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.file.gateway.client.entity.ApiClient;
import com.file.gateway.client.repository.ApiClientRepository;
import com.file.gateway.common.response.ApiResponse;
import com.file.gateway.common.response.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiClientRepository apiClientRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (!StringUtils.hasText(apiKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 이미 인증된 경우 pass-through
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ApiClient> clientOpt = apiClientRepository.findByApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            sendUnauthorized(response, ErrorCode.INVALID_API_KEY);
            return;
        }

        ApiClient apiClient = clientOpt.get();
        if (!"ACTIVE".equals(apiClient.getStatus())) {
            sendUnauthorized(response, ErrorCode.INVALID_API_KEY);
            return;
        }

        ApiClientPrincipal principal = ApiClientPrincipal.from(apiClient);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.fail(errorCode, errorCode.getDefaultMessage())
                )
        );
    }
}
