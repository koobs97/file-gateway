package com.file.gateway.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.file.gateway.auth.ApiKeyAuthenticationFilter;
import com.file.gateway.auth.JwtAuthenticationFilter;
import com.file.gateway.common.response.ApiResponse;
import com.file.gateway.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig
 * - Spring Security 전체 보안 정책을 정의하는 설정 클래스
 * - JWT 및 API 키 기반 Stateless 인증 필터 체인 구성
 * - CORS, CSRF, 인가 규칙, 예외 응답을 일괄 설정
 * - ADMIN/AUDITOR/END_USER/API_CLIENT 역할별 엔드포인트 접근 제어
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** JWT 토큰 검증 필터 */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** API 키 헤더 검증 필터 */
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    /** JSON 직렬화를 위한 ObjectMapper */
    private final ObjectMapper objectMapper;

    /**
     * Spring Security 필터 체인을 구성한다.
     * - CSRF 비활성화, CORS 설정, Stateless 세션 정책 적용
     * - 공개 엔드포인트(인증, Swagger, WebSocket)를 제외한 모든 요청에 인증 요구
     * - 인증 실패 시 401, 인가 실패 시 403 JSON 응답 반환
     *
     * @param http HttpSecurity 빌더
     * @return 구성된 SecurityFilterChain
     * @throws Exception 필터 체인 빌드 중 예외 발생 시
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/audit/**").hasAnyRole("ADMIN", "AUDITOR")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthenticationFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(
                                            ApiResponse.fail(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage())
                                    )
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(
                                            ApiResponse.fail(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage())
                                    )
                            );
                        })
                );

        return http.build();
    }

    /**
     * BCrypt 기반 패스워드 인코더 빈을 등록한다.
     *
     * @return BCryptPasswordEncoder 인스턴스
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Spring Security의 AuthenticationManager 빈을 등록한다.
     *
     * @param config AuthenticationConfiguration
     * @return AuthenticationManager 인스턴스
     * @throws Exception AuthenticationManager 생성 중 예외 발생 시
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS 허용 정책을 정의한다.
     * - 허용 오리진: localhost:5173 (Vite), localhost:3000
     * - 허용 메서드: GET, POST, PUT, PATCH, DELETE, OPTIONS
     * - 인증 정보(쿠키 등) 포함 허용
     *
     * @return CORS 설정 소스
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
