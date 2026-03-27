package com.file.gateway.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SwaggerConfig
 * - SpringDoc OpenAPI 3.0 기반 API 문서 자동 생성 설정 클래스
 * - /swagger-ui.html 경로에서 API 명세를 확인할 수 있다
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Configuration
public class SwaggerConfig {

    /**
     * OpenAPI 메타 정보(제목, 설명, 버전)를 정의하는 빈을 등록한다.
     *
     * @return OpenAPI 명세 객체
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("File Gateway API")
                        .description("CDR 기반 MS Office 파일 무해화 시스템 API")
                        .version("v1.0.0"));
    }
}
