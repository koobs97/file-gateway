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
public class XlsxAnalyzer implements OfficeAnalyzer {

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

    @Override
    public boolean supports(String extension) {
        return "xlsx".equalsIgnoreCase(extension);
    }
}
