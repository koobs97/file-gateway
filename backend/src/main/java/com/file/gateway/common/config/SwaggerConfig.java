package com.file.gateway.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("File Gateway API")
                        .description("CDR 기반 MS Office 파일 무해화 시스템 API")
                        .version("v1.0.0"));
    }
}
