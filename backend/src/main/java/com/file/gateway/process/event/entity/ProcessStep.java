package com.file.gateway.process.event.entity;

/**
 * ProcessStep
 * - CDR 처리 파이프라인의 각 단계를 정의하는 열거형
 * - FileProcessLog 엔티티에서 어느 처리 단계의 로그인지 구분하는 데 사용
 *
 * @author 구본상
 * @since 2026-03-26
 */
public enum ProcessStep {
    /** 파일 업로드 단계 */
    UPLOAD,

    /** 위협 요소 탐지를 위한 분석 단계 */
    ANALYSIS,

    /** 위협 요소 제거 및 재구성 단계 */
    SANITIZE,

    /** 무해화된 파일 저장 단계 */
    SAVE
}
