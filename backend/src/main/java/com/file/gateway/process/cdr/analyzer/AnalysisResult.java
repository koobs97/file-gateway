package com.file.gateway.process.cdr.analyzer;

import java.util.List;

/**
 * AnalysisResult
 * - Office 파일 분석 결과를 담는 불변 레코드
 * - 매크로, 외부 링크, OLE 객체 탐지 여부와 위협 목록을 보유
 * - isSafe() 를 통해 파일의 안전 여부를 단일 메서드로 판단 가능
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record AnalysisResult(
        /** VBA 매크로 포함 여부 */
        boolean hasMacro,
        /** 외부 링크 포함 여부 */
        boolean hasExternalLinks,
        /** OLE 임베디드 객체 포함 여부 */
        boolean hasOleObject,
        /** 탐지된 위협 항목 설명 목록 */
        List<String> detectedThreats
) {
    /**
     * 파일이 안전한지 여부를 반환한다.
     * 매크로, 외부 링크, OLE 객체가 모두 없을 경우 안전으로 판단한다.
     *
     * @return 위협 요소가 하나도 없으면 true, 하나라도 있으면 false
     */
    public boolean isSafe() {
        return !hasMacro && !hasExternalLinks && !hasOleObject;
    }

    /**
     * 위협 요소가 전혀 없는 안전한 AnalysisResult 인스턴스를 생성한다.
     *
     * @return 모든 위협 플래그가 false이고 detectedThreats가 빈 목록인 AnalysisResult
     */
    public static AnalysisResult safe() {
        return new AnalysisResult(false, false, false, List.of());
    }
}
