package com.file.gateway.file.entity;

/**
 * FileStatus
 * - 업로드된 파일의 CDR(Content Disarm and Reconstruction) 처리 상태를 나타내는 열거형이다.
 * - 파일은 UPLOADED → PROCESSING → DONE 또는 FAIL 순으로 상태가 전이된다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public enum FileStatus {
    /** 파일이 업로드되어 저장 완료된 상태 */
    UPLOADED,
    /** CDR 처리가 진행 중인 상태 */
    PROCESSING,
    /** CDR 처리가 성공적으로 완료된 상태 */
    DONE,
    /** CDR 처리 중 오류가 발생하여 실패한 상태 */
    FAIL
}
