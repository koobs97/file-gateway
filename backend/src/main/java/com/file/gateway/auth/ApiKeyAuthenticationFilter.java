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

/**
 * ApiKeyAuthenticationFilter
 * - "X-API-Key" 요청 헤더를 통한 API 클라이언트 인증 처리
 * - 요청당 단 한 번만 실행되도록 OncePerRequestFilter를 상속
 * - 유효한 API 키 확인 후 ROLE_API_CLIENT 권한으로 SecurityContext에 등록
 * - API 키가 없거나 이미 인증된 요청은 다음 필터로 pass-through
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** API 키를 전달하는 HTTP 요청 헤더 이름 */
    private static final String API_KEY_HEADER = "X-API-Key";

    /** API 클라이언트 조회를 위한 Repository */
    private final ApiClientRepository apiClientRepository;

    /** JSON 직렬화를 위한 ObjectMapper */
    private final ObjectMapper objectMapper;

    /**
     * 요청마다 X-API-Key 헤더를 검사하여 API 클라이언트 인증을 처리
     *
     * @param request     HTTP 요청 객체 (null 불허)
     * @param response    HTTP 응답 객체 (null 불허)
     * @param filterChain 다음 필터로 요청을 전달하는 체인 (null 불허)
     * @throws ServletException 필터 처리 중 서블릿 예외 발생 시
     * @throws IOException      I/O 처리 중 예외 발생 시
     */
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

    /**
     * 인증 실패 시 HTTP 401 상태와 JSON 오류 응답을 클라이언트에 전송
     *
     * @param response  HTTP 응답 객체 (null 불허)
     * @param errorCode 응답에 포함할 에러 코드 (null 불허)
     * @throws IOException 응답 쓰기 중 I/O 예외 발생 시
     */
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
