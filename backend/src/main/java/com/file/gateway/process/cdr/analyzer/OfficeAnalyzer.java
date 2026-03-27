package com.file.gateway.process.cdr.analyzer;

import java.io.IOException;
import java.io.InputStream;

/**
 * OfficeAnalyzer
 * - MS Office 파일(docx/xlsx/pptx)의 위협 요소를 분석하는 인터페이스
 * - 파일 형식별 구현체(DocxAnalyzer, XlsxAnalyzer, PptxAnalyzer)가 실제 분석 로직을 담당
 * - CDR 파이프라인에서 무해화 전 단계로 호출됨
 *
 * @author 구본상
 * @since 2026-03-26
 */
public interface OfficeAnalyzer {

    /**
     * 입력 스트림으로부터 Office 파일을 읽어 위협 요소를 분석한다.
     *
     * @param in 분석할 파일의 입력 스트림
     * @return 탐지된 위협 정보를 담은 AnalysisResult
     * @throws IOException 파일 읽기 중 I/O 오류 발생 시
     */
    AnalysisResult analyze(InputStream in) throws IOException;

    /**
     * 해당 구현체가 주어진 파일 확장자를 지원하는지 여부를 반환한다.
     *
     * @param extension 파일 확장자 (예: "docx", "xlsx", "pptx")
     * @return 지원 여부 (true: 지원, false: 미지원)
     */
    boolean supports(String extension);
}
