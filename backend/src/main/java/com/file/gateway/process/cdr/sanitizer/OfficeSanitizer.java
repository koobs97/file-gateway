package com.file.gateway.process.cdr.sanitizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * OfficeSanitizer
 * - MS Office 파일(docx/xlsx/pptx)의 위협 요소를 제거(무해화)하는 인터페이스
 * - 파일 형식별 구현체(DocxSanitizer, XlsxSanitizer, PptxSanitizer)가 실제 제거 로직을 담당
 * - CDR 파이프라인에서 분석(OfficeAnalyzer) 이후 단계로 호출됨
 *
 * @author 구본상
 * @since 2026-03-26
 */
public interface OfficeSanitizer {

    /**
     * 입력 스트림으로부터 Office 파일을 읽어 위협 요소를 제거한 후 출력 스트림에 기록한다.
     *
     * @param in  무해화할 원본 파일의 입력 스트림
     * @param out 무해화된 파일을 기록할 출력 스트림
     * @throws IOException 파일 읽기·쓰기 중 I/O 오류 발생 시
     */
    void sanitize(InputStream in, OutputStream out) throws IOException;

    /**
     * 해당 구현체가 주어진 파일 확장자를 지원하는지 여부를 반환한다.
     *
     * @param extension 파일 확장자 (예: "docx", "xlsx", "pptx")
     * @return 지원 여부 (true: 지원, false: 미지원)
     */
    boolean supports(String extension);
}
