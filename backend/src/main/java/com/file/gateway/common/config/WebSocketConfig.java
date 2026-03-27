package com.file.gateway.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocketConfig
 * - STOMP 기반 WebSocket 메시지 브로커를 설정하는 구성 클래스
 * - CDR 처리 결과를 클라이언트에 실시간으로 푸시하는 채널을 제공
 * - SockJS 폴백을 통해 WebSocket 미지원 환경에서도 동작 보장
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 메시지 브로커 및 애플리케이션 목적지 프리픽스를 구성한다.
     * - 인메모리 브로커: /topic (구독 채널)
     * - 클라이언트 발행 프리픽스: /app
     *
     * @param registry MessageBrokerRegistry 설정 객체
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * STOMP 연결 엔드포인트를 등록한다.
     * - 엔드포인트 경로: /ws
     * - 모든 오리진 허용, SockJS 폴백 활성화
     *
     * @param registry StompEndpointRegistry 설정 객체
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
