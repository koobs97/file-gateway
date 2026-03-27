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
 * DocxAnalyzer
 * - DOCX 파일(ZIP 구조)에서 위협 요소를 분석하는 구현체
 * - VBA 매크로(word/vbaProject.bin) 존재 여부 탐지
 * - 외부 링크(word/externalLinks/) 디렉터리 엔트리 존재 여부 탐지
 * - OfficeAnalyzer 인터페이스를 구현하며 "docx" 확장자를 지원
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Component
public class DocxAnalyzer implements OfficeAnalyzer {

    /**
     * DOCX 파일 입력 스트림을 ZIP으로 열어 위협 요소를 분석한다.
     * VBA 매크로 바이너리와 외부 링크 엔트리를 탐지하여 결과를 반환한다.
     *
     * @param in 분석할 DOCX 파일의 입력 스트림
     * @return 탐지된 매크로·외부 링크 정보를 포함한 AnalysisResult
     * @throws IOException ZIP 엔트리 읽기 중 I/O 오류 발생 시
     */
    @Override
    public AnalysisResult analyze(InputStream in) throws IOException {
        List<String> threats = new ArrayList<>();
        boolean hasMacro = false;
        boolean hasExternalLinks = false;

        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("word/vbaProject.bin".equals(name)) {
                    hasMacro = true;
                    threats.add("VBA 매크로 탐지: " + name);
                    log.debug("[CDR] VBA 매크로 발견: {}", name);
                }
                if (name.startsWith("word/externalLinks/") && !entry.isDirectory()) {
                    hasExternalLinks = true;
                    threats.add("외부 링크 탐지: " + name);
                    log.debug("[CDR] 외부 링크 발견: {}", name);
                }
                zip.closeEntry();
            }
        }

        return new AnalysisResult(hasMacro, hasExternalLinks, false, threats);
    }

    /**
     * 해당 분석기가 "docx" 확장자를 지원하는지 여부를 반환한다.
     *
     * @param extension 파일 확장자 문자열 (대소문자 무관)
     * @return "docx"이면 true, 그 외 false
     */
    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension);
    }
}
