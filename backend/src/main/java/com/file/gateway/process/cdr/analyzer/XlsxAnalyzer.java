package com.file.gateway.process.cdr.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * XlsxAnalyzer
 * - XLSX 파일(ZIP 구조)에서 위협 요소를 분석하는 구현체
 * - 외부 링크(xl/externalLinks/) 디렉터리 엔트리 존재 여부 탐지
 * - OfficeAnalyzer 인터페이스를 구현하며 "xlsx" 확장자를 지원
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Component
public class XlsxAnalyzer implements OfficeAnalyzer {

    /**
     * XLSX 파일 입력 스트림을 ZIP으로 열어 위협 요소를 분석한다.
     * xl/externalLinks/ 경로의 엔트리를 탐지하여 외부 링크 여부를 판단한다.
     *
     * @param in 분석할 XLSX 파일의 입력 스트림
     * @return 탐지된 외부 링크 정보를 포함한 AnalysisResult
     * @throws IOException ZIP 엔트리 읽기 중 I/O 오류 발생 시
     */
    @Override
    public AnalysisResult analyze(InputStream in) throws IOException {
        List<String> threats = new ArrayList<>();
        boolean hasExternalLinks = false;

        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("xl/externalLinks/") && !entry.isDirectory()) {
                    hasExternalLinks = true;
                    threats.add("외부 링크 탐지: " + name);
                    log.debug("[CDR] 외부 링크 발견: {}", name);
                }
                zip.closeEntry();
            }
        }

        return new AnalysisResult(false, hasExternalLinks, false, threats);
    }

    /**
     * 해당 분석기가 "xlsx" 확장자를 지원하는지 여부를 반환한다.
     *
     * @param extension 파일 확장자 문자열 (대소문자 무관)
     * @return "xlsx"이면 true, 그 외 false
     */
    @Override
    public boolean supports(String extension) {
        return "xlsx".equalsIgnoreCase(extension);
    }
}
