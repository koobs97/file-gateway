package com.file.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * FileGatewayApplication
 * - CDR(Content Disarm and Reconstruction) 기반 MS Office 파일 무해화 시스템의 Spring Boot 진입점
 * - JPA Auditing, ConfigurationProperties 스캔, Spring Data Web 지원을 활성화
 * - 페이지 직렬화는 DTO 방식(VIA_DTO)을 사용하여 안정적인 JSON 응답을 보장
 *
 * @author 구본상
 * @since 2026-03-26
 */
@EnableJpaAuditing
@ConfigurationPropertiesScan
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@SpringBootApplication
public class FileGatewayApplication {

    /**
     * 애플리케이션 진입점 메서드.
     *
     * @param args 커맨드라인 실행 인수
     */
    public static void main(String[] args) {
        SpringApplication.run(FileGatewayApplication.class, args);
    }

}
