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
 * OOXML(docx/xlsx/pptx) 파일은 ZIP 구조이므로,
 * ZIP 엔트리 제거 + XML 참조 정리 공통 로직을 제공한다.
 */
@Slf4j
public abstract class AbstractZipSanitizer implements OfficeSanitizer {

    /** 제거 대상 엔트리인지 판단. 서브클래스에서 구현. */
    protected abstract boolean shouldRemove(String entryName);

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
     * 예: <Override PartName="/word/vbaProject.bin" ContentType="..."/>
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
     * 예: <Relationship Target="vbaProject.bin" .../>
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

    private Document parseXml(byte[] content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // XXE 방지 (악성 파일 처리 시 필수)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
    }

    private byte[] serializeXml(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(baos));
        return baos.toByteArray();
    }
}
