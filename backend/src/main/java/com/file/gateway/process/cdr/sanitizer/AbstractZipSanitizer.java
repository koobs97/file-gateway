package com.file.gateway.process.cdr.sanitizer;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * AbstractZipSanitizer
 * - OOXML(docx/xlsx/pptx) 파일은 ZIP 구조이므로, ZIP 엔트리 제거 및 XML 참조 정리 공통 로직을 제공하는 추상 클래스
 * - 서브클래스(DocxSanitizer, XlsxSanitizer, PptxSanitizer)는 shouldRemove() 만 구현하면 됨
 * - [Content_Types].xml과 .rels 파일에서 제거된 엔트리의 참조도 함께 삭제하여 파일 무결성 유지
 * - XXE(XML External Entity) 공격 방지를 위한 파서 설정 포함
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
public abstract class AbstractZipSanitizer implements OfficeSanitizer {

    /**
     * 해당 ZIP 엔트리를 제거 대상으로 판단할지 여부를 반환한다.
     * 서브클래스에서 파일 형식에 맞는 제거 기준을 구현한다.
     *
     * @param entryName ZIP 엔트리 이름 (예: "word/vbaProject.bin")
     * @return 제거 대상이면 true, 유지 대상이면 false
     */
    protected abstract boolean shouldRemove(String entryName);

    /**
     * 입력 스트림의 OOXML 파일에서 위협 엔트리를 제거하고 무해화된 ZIP을 출력 스트림에 기록한다.
     * 1단계: 제거 대상 엔트리 목록을 수집한다.
     * 2단계: 위협 엔트리를 제외하고 ZIP을 재조립하며, [Content_Types].xml과 .rels 참조도 정리한다.
     *
     * @param in  무해화할 원본 OOXML 파일의 입력 스트림
     * @param out 무해화된 파일을 기록할 출력 스트림
     * @throws IOException ZIP 읽기·쓰기 중 I/O 오류 발생 시
     */
    @Override
    public void sanitize(InputStream in, OutputStream out) throws IOException {
        byte[] data = in.readAllBytes();

        // 1단계: 제거할 엔트리 목록 수집
        Set<String> removedEntries = collectEntriesToRemove(data);

        if (removedEntries.isEmpty()) {
            out.write(data);
            return;
        }

        // 2단계: 정제된 ZIP 재조립
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data));
             ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();

                if (removedEntries.contains(name)) {
                    log.info("[CDR] 위협 요소 제거: {}", name);
                    continue;
                }

                byte[] content = zin.readAllBytes();

                // 참조 정리: Content_Types와 .rels 파일에서 제거된 엔트리 참조 삭제
                if ("[Content_Types].xml".equals(name)) {
                    content = cleanContentTypes(content, removedEntries);
                } else if (name.endsWith(".rels")) {
                    content = cleanRelationships(content, removedEntries);
                }

                zout.putNextEntry(new ZipEntry(name));
                zout.write(content);
                zout.closeEntry();
            }
        }
    }

    /**
     * ZIP 바이트 배열을 순회하여 shouldRemove() 기준에 해당하는 엔트리 이름을 수집한다.
     *
     * @param data 원본 OOXML 파일의 바이트 배열
     * @return 제거 대상 엔트리 이름의 집합 (삽입 순서 유지)
     * @throws IOException ZIP 읽기 중 I/O 오류 발생 시
     */
    private Set<String> collectEntriesToRemove(byte[] data) throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (shouldRemove(entry.getName())) {
                    entries.add(entry.getName());
                }
            }
        }
        return entries;
    }

    /**
     * [Content_Types].xml에서 제거된 엔트리의 Override 요소를 삭제한다.
     * 예: {@code <Override PartName="/word/vbaProject.bin" ContentType="..."/>}
     *
     * @param content       [Content_Types].xml 파일의 바이트 배열
     * @param removedEntries 제거된 ZIP 엔트리 이름 집합
     * @return 참조가 정리된 [Content_Types].xml 바이트 배열; 파싱 실패 시 원본 반환
     */
    private byte[] cleanContentTypes(byte[] content, Set<String> removedEntries) {
        try {
            Document doc = parseXml(content);
            NodeList overrides = doc.getElementsByTagNameNS("*", "Override");
            List<Node> toRemove = new ArrayList<>();
            for (int i = 0; i < overrides.getLength(); i++) {
                Element el = (Element) overrides.item(i);
                String partName = el.getAttribute("PartName"); // 예: /word/vbaProject.bin
                for (String removed : removedEntries) {
                    if (partName.equals("/" + removed)) {
                        toRemove.add(el);
                        break;
                    }
                }
            }
            toRemove.forEach(n -> n.getParentNode().removeChild(n));
            return serializeXml(doc);
        } catch (Exception e) {
            log.warn("[CDR] [Content_Types].xml 정리 실패, 원본 유지: {}", e.getMessage());
            return content;
        }
    }

    /**
     * .rels 파일에서 제거된 엔트리를 가리키는 Relationship 요소를 삭제한다.
     * 예: {@code <Relationship Target="vbaProject.bin" .../>}
     *
     * @param content        .rels 파일의 바이트 배열
     * @param removedEntries 제거된 ZIP 엔트리 이름 집합
     * @return 참조가 정리된 .rels 바이트 배열; 파싱 실패 시 원본 반환
     */
    private byte[] cleanRelationships(byte[] content, Set<String> removedEntries) {
        try {
            // 제거된 경로의 파일명만 추출 (예: word/vbaProject.bin → vbaProject.bin)
            Set<String> removedFileNames = new LinkedHashSet<>();
            for (String entry : removedEntries) {
                removedFileNames.add(entry.substring(entry.lastIndexOf('/') + 1));
            }

            Document doc = parseXml(content);
            NodeList relationships = doc.getElementsByTagNameNS("*", "Relationship");
            List<Node> toRemove = new ArrayList<>();
            for (int i = 0; i < relationships.getLength(); i++) {
                Element el = (Element) relationships.item(i);
                String target = el.getAttribute("Target"); // 예: vbaProject.bin
                String targetFileName = target.substring(target.lastIndexOf('/') + 1);
                if (removedFileNames.contains(targetFileName)) {
                    toRemove.add(el);
                }
            }
            toRemove.forEach(n -> n.getParentNode().removeChild(n));
            return serializeXml(doc);
        } catch (Exception e) {
            log.warn("[CDR] .rels 파일 정리 실패, 원본 유지: {}", e.getMessage());
            return content;
        }
    }

    /**
     * 바이트 배열을 XML Document로 파싱한다.
     * XXE(XML External Entity) 공격 방지를 위해 외부 엔티티 처리를 비활성화한다.
     *
     * @param content 파싱할 XML 바이트 배열
     * @return 파싱된 DOM Document 객체
     * @throws Exception XML 파싱 또는 파서 설정 중 오류 발생 시
     */
    private Document parseXml(byte[] content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // XXE 방지 (악성 파일 처리 시 필수)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
    }

    /**
     * DOM Document 객체를 바이트 배열로 직렬화한다.
     *
     * @param doc 직렬화할 DOM Document 객체
     * @return XML 직렬화 결과 바이트 배열
     * @throws Exception 직렬화 중 오류 발생 시
     */
    private byte[] serializeXml(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(baos));
        return baos.toByteArray();
    }
}
