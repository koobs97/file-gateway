package com.file.gateway.process.cdr.analyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocxAnalyzerTest {

    private final DocxAnalyzer analyzer = new DocxAnalyzer();

    @Test
    @DisplayName("VBA 매크로가 있는 docx 분석 시 hasMacro=true, isSafe=false 반환")
    void analyze_WithVbaMacro_DetectsMacro() throws IOException {
        // given
        InputStream docx = buildMockDocx(true, false);

        // when
        AnalysisResult result = analyzer.analyze(docx);

        // then
        assertThat(result.hasMacro()).isTrue();
        assertThat(result.hasExternalLinks()).isFalse();
        assertThat(result.isSafe()).isFalse();
        assertThat(result.detectedThreats()).hasSize(1);
    }

    @Test
    @DisplayName("외부 링크가 있는 docx 분석 시 hasExternalLinks=true 반환")
    void analyze_WithExternalLinks_DetectsLinks() throws IOException {
        // given
        InputStream docx = buildMockDocx(false, true);

        // when
        AnalysisResult result = analyzer.analyze(docx);

        // then
        assertThat(result.hasMacro()).isFalse();
        assertThat(result.hasExternalLinks()).isTrue();
        assertThat(result.isSafe()).isFalse();
        assertThat(result.detectedThreats()).hasSize(1);
    }

    @Test
    @DisplayName("위협 없는 docx 분석 시 isSafe=true, detectedThreats 비어있음")
    void analyze_CleanDocx_ReturnsSafe() throws IOException {
        // given
        InputStream docx = buildMockDocx(false, false);

        // when
        AnalysisResult result = analyzer.analyze(docx);

        // then
        assertThat(result.isSafe()).isTrue();
        assertThat(result.detectedThreats()).isEmpty();
    }

    @Test
    @DisplayName("supports()는 docx(대소문자 무관)에 true, xlsx에 false를 반환한다")
    void supports_ReturnsCorrectly() {
        assertThat(analyzer.supports("docx")).isTrue();
        assertThat(analyzer.supports("DOCX")).isTrue();
        assertThat(analyzer.supports("xlsx")).isFalse();
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────────

    private InputStream buildMockDocx(boolean withMacro, boolean withExternalLinks) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            addEntry(zip, "word/document.xml", "<document/>");
            if (withMacro) {
                addEntry(zip, "word/vbaProject.bin", "VBA_CONTENT");
            }
            if (withExternalLinks) {
                addEntry(zip, "word/externalLinks/externalLink1.xml", "<externalLink/>");
            }
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private void addEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes());
        zip.closeEntry();
    }
}
