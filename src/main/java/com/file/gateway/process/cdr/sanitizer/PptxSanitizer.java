package com.file.gateway.process.cdr.sanitizer;

import org.springframework.stereotype.Component;

/**
 * PPTX 무해화: VBA 매크로(ppt/vbaProject.bin) 및 임베디드 OLE 객체(ppt/embeddings/) 제거
 */
@Component
public class PptxSanitizer extends AbstractZipSanitizer {

    @Override
    protected boolean shouldRemove(String name) {
        return "ppt/vbaProject.bin".equals(name)
                || name.startsWith("ppt/embeddings/");
    }

    @Override
    public boolean supports(String extension) {
        return "pptx".equalsIgnoreCase(extension);
    }
}
