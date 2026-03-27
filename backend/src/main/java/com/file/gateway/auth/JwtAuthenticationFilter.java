package com.file.gateway.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter
 * - HTTP 요청의 Authorization 헤더에서 Bearer 토큰을 추출하여 JWT 인증을 처리
 * - 요청당 단 한 번만 실행되도록 OncePerRequestFilter를 상속
 * - 토큰 검증 성공 시 DB에서 최신 사용자 정보를 재조회하여 SecurityContext에 등록
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 토큰 생성 및 검증 컴포넌트 */
    private final JwtTokenProvider jwtTokenProvider;

    /** DB 기반 사용자 정보 로드 서비스 */
    private final CustomUserDetailsService userDetailsService;

    /**
     * 요청마다 JWT 토큰을 추출·검증하고 인증 정보를 SecurityContext에 설정
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
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            // DB에서 최신 role/active 재조회 (동적 권한 변경 즉각 반영)
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * HTTP 요청의 Authorization 헤더에서 "Bearer " 접두사를 제거하고 순수 토큰 값을 추출
     *
     * @param request Authorization 헤더를 포함한 HTTP 요청 객체 (null 불허)
     * @return 추출된 JWT 토큰 문자열, 헤더가 없거나 형식이 맞지 않으면 null
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
