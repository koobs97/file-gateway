package com.file.gateway.process.cdr.sanitizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface OfficeSanitizer {
    void sanitize(InputStream in, OutputStream out) throws IOException;
    boolean supports(String extension);
}
