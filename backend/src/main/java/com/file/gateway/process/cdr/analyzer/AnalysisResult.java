package com.file.gateway.process.cdr.analyzer;

import java.util.List;

public record AnalysisResult(
        boolean hasMacro,
        boolean hasExternalLinks,
        boolean hasOleObject,
        List<String> detectedThreats
) {
    public boolean isSafe() {
        return !hasMacro && !hasExternalLinks && !hasOleObject;
    }

    public static AnalysisResult safe() {
        return new AnalysisResult(false, false, false, List.of());
    }
}
