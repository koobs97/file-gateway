package com.file.gateway.process.cdr.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class PptxAnalyzer implements OfficeAnalyzer {

    @Override
    public AnalysisResult analyze(InputStream in) throws IOException {
        List<String> threats = new ArrayList<>();
        boolean hasMacro = false;
        boolean hasOleObject = false;

        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("ppt/vbaProject.bin".equals(name)) {
                    hasMacro = true;
                    threats.add("VBA 매크로 탐지: " + name);
                    log.debug("[CDR] VBA 매크로 발견: {}", name);
                }
                if (name.startsWith("ppt/embeddings/") && !entry.isDirectory()) {
                    hasOleObject = true;
                    threats.add("임베디드 OLE 객체 탐지: " + name);
                    log.debug("[CDR] OLE 객체 발견: {}", name);
                }
                zip.closeEntry();
            }
        }

        return new AnalysisResult(hasMacro, false, hasOleObject, threats);
    }

    @Override
    public boolean supports(String extension) {
        return "pptx".equalsIgnoreCase(extension);
    }
}
