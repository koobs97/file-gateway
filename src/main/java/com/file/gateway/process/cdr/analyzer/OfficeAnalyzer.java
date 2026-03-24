package com.file.gateway.process.cdr.analyzer;

import java.io.IOException;
import java.io.InputStream;

public interface OfficeAnalyzer {
    AnalysisResult analyze(InputStream in) throws IOException;
    boolean supports(String extension);
}
