package com.file.gateway.process.cdr.sanitizer;

import org.springframework.stereotype.Component;

/**
 * XLSX 무해화: 외부 링크(xl/externalLinks/) 제거
 */
@Component
public class XlsxSanitizer extends AbstractZipSanitizer {

    @Override
    protected boolean shouldRemove(String name) {
        return name.startsWith("xl/externalLinks/");
    }

    @Override
    public boolean supports(String extension) {
        return "xlsx".equalsIgnoreCase(extension);
    }
}
