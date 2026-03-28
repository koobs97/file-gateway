package com.file.gateway.common.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * HealthController
 * - 애플리케이션 헬스체크 엔드포인트를 제공한다.
 * - Docker 컨테이너 헬스체크 및 Blue-Green 배포 시 신규 슬롯의 준비 상태 확인에 사용된다.
 * - 인증 없이 접근 가능하도록 SecurityConfig에서 permitAll 처리된다.
 *
 * @author 구본상
 * @since 2026-03-28
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 애플리케이션 상태를 반환한다.
     *
     * @return {"status": "UP"} (HTTP 200)
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
