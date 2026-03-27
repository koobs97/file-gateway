package com.file.gateway.process.cdr.sanitizer;

import org.springframework.stereotype.Component;

/**
 * XlsxSanitizer
 * - XLSX 파일에서 위협 요소를 제거하는 무해화 구현체
 * - 외부 링크(xl/externalLinks/) 디렉터리 하위 엔트리 전체 제거
 * - AbstractZipSanitizer를 상속하여 "xlsx" 확장자에 대한 shouldRemove() 기준을 정의
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Component
public class XlsxSanitizer extends AbstractZipSanitizer {

    /**
     * 해당 ZIP 엔트리가 XLSX 위협 요소에 해당하는지 판단한다.
     * xl/externalLinks/ 하위 경로이면 제거 대상으로 판단한다.
     *
     * @param name ZIP 엔트리 이름
     * @return 제거 대상이면 true, 유지 대상이면 false
     */
    @Override
    protected boolean shouldRemove(String name) {
        return name.startsWith("xl/externalLinks/");
    }

    /**
     * 해당 무해화기가 "xlsx" 확장자를 지원하는지 여부를 반환한다.
     *
     * @param extension 파일 확장자 문자열 (대소문자 무관)
     * @return "xlsx"이면 true, 그 외 false
     */
    @Override
    public boolean supports(String extension) {
        return "xlsx".equalsIgnoreCase(extension);
    }
}
