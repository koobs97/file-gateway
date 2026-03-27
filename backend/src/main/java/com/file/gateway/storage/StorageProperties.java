package com.file.gateway.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * StorageProperties
 * - application.yml의 app.storage 접두사 설정값을 바인딩하는 프로퍼티 클래스이다.
 * - 파일 저장 기본 경로, 최대 파일 크기, 허용 확장자 목록을 관리한다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** 파일 저장 기본 경로 (기본값: ./uploads) */
    private String basePath = "./uploads";

    /** 허용 최대 파일 크기 (바이트 단위, 기본값: 100MB) */
    private long maxFileSize = 104857600L;

    /** 허용 확장자 목록 (쉼표 구분 문자열, 기본값: docx,xlsx,pptx) */
    private String allowedExtensions = "docx,xlsx,pptx";

    /**
     * 허용 확장자 문자열을 List로 변환하여 반환한다.
     *
     * @return 허용 확장자 문자열 리스트 (예: ["docx", "xlsx", "pptx"])
     */
    public List<String> getAllowedExtensionList() {
        return Arrays.asList(allowedExtensions.split(","));
    }
}
