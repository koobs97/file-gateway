package com.file.gateway.process.cdr.sanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocxSanitizerTest {

    private final DocxSanitizer sanitizer = new DocxSanitizer();

    @Test
    @DisplayName("VBA 매크로 파일이 제거되고 XML 참조도 정리된다")
    void sanitize_RemovesVbaMacroAndCleansReferences() throws IOException {
        // given
        byte[] docx = buildMockDocx(true, false);

        // when
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sanitizer.sanitize(new ByteArrayInputStream(docx), out);
        byte[] result = out.toByteArray();

        // then - 매크로 파일 제거
        assertThat(readZipEntries(result)).doesNotContain("word/vbaProject.bin");
        assertThat(readZipEntries(result)).contains("word/document.xml");

        // then - [Content_Types].xml에서 참조 제거
        String contentTypes = new String(readZipEntryContent(result, "[Content_Types].xml"));
        assertThat(contentTypes).doesNotContain("vbaProject.bin");

        // then - .rels 파일에서 관계 제거
        String rels = new String(readZipEntryContent(result, "word/_rels/document.xml.rels"));
        assertThat(rels).doesNotContain("vbaProject.bin");
    }

    @Test
    @DisplayName("외부 링크 파일이 제거된다")
    void sanitize_RemovesExternalLinks() throws IOException {
        // given
        byte[] docx = buildMockDocx(false, true);

        // when
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sanitizer.sanitize(new ByteArrayInputStream(docx), out);

        // then
        Set<String> entries = readZipEntries(out.toByteArray());
        assertThat(entries).noneMatch(e -> e.startsWith("word/externalLinks/"));
        assertThat(entries).contains("word/document.xml");
    }

    @Test
    @DisplayName("위협 없는 파일은 모든 엔트리를 그대로 유지한다")
    void sanitize_CleanFile_PreservesAllEntries() throws IOException {
        // given
        byte[] docx = buildMockDocx(false, false);

        // when
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sanitizer.sanitize(new ByteArrayInputStream(docx), out);

        // then
        Set<String> entries = readZipEntries(out.toByteArray());
        assertThat(entries).contains("word/document.xml", "[Content_Types].xml");
    }

    @Test
    @DisplayName("supports()는 docx에 true, xlsx에 false를 반환한다")
    void supports_ReturnsCorrectly() {
        assertThat(sanitizer.supports("docx")).isTrue();
        assertThat(sanitizer.supports("xlsx")).isFalse();
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────────

    private byte[] buildMockDocx(boolean withMacro, boolean withExternalLinks) throws IOException {
        String relsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                (withMacro ? "<Relationship Id=\"rId2\" Type=\"http://schemas.microsoft.com/office/2006/relationships/vbaProject\" Target=\"vbaProject.bin\"/>" : "") +
                "</Relationships>";

        String contentTypesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                (withMacro ? "<Override PartName=\"/word/vbaProject.bin\" ContentType=\"application/vnd.ms-office.activeX+xml\"/>" : "") +
                "</Types>";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            addEntry(zip, "[Content_Types].xml", contentTypesXml);
            addEntry(zip, "word/document.xml", "<document/>");
            addEntry(zip, "word/_rels/document.xml.rels", relsXml);
            if (withMacro) {
                addEntry(zip, "word/vbaProject.bin", "VBA_CONTENT");
            }
            if (withExternalLinks) {
                addEntry(zip, "word/externalLinks/externalLink1.xml", "<externalLink/>");
            }
        }
        return baos.toByteArray();
    }

    private void addEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes());
        zip.closeEntry();
    }

    private Set<String> readZipEntries(byte[] data) throws IOException {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    private byte[] readZipEntryContent(byte[] data, String entryName) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return zin.readAllBytes();
                }
            }
        }
        return new byte[0];
    }
}
