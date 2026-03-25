package com.file.gateway.process.cdr.sanitizer;

import org.springframework.stereotype.Component;

/**
 * DOCX 무해화: VBA 매크로(word/vbaProject.bin) 및 외부 링크(word/externalLinks/) 제거
 */
@Component
public class DocxSanitizer extends AbstractZipSanitizer {

    @Override
    protected boolean shouldRemove(String name) {
        return "word/vbaProject.bin".equals(name)
                || name.startsWith("word/externalLinks/");
    }

    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension);
    }
}
