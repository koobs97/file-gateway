package com.file.gateway.process.event.entity;

/**
 * ProcessStatus
 * - CDR 처리 각 단계의 성공/실패 결과를 나타내는 열거형
 * - FileProcessLog 엔티티에서 단계별 처리 결과를 기록하는 데 사용
 *
 * @author 구본상
 * @since 2026-03-26
 */
public enum ProcessStatus {
    /** 처리 단계가 성공적으로 완료된 상태 */
    SUCCESS,

    /** 처리 단계에서 오류가 발생하여 실패한 상태 */
    FAIL
}
