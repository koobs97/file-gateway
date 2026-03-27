package com.file.gateway.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AsyncConfig
 * - Spring의 비동기 처리(@Async)를 활성화하고 CDR 무해화 전용 스레드 풀을 구성하는 설정 클래스
 * - 코어 5개, 최대 20개 스레드와 100개의 대기 큐를 제공하여 대용량 파일 처리를 지원
 *
 * @author 구본상
 * @since 2026-03-26
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    /**
     * CDR 무해화 작업 전용 스레드 풀 Executor 빈을 등록한다.
     * - 스레드 이름 접두사: {@code sanitize-}
     * - 코어 스레드 수: 5, 최대 스레드 수: 20, 큐 용량: 100
     *
     * @return 초기화된 ThreadPoolTaskExecutor
     */
    @Bean(name = "sanitizeExecutor")
    public Executor sanitizeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sanitize-");
        executor.initialize();
        return executor;
    }
}
