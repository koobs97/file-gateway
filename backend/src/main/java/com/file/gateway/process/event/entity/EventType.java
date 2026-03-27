package com.file.gateway.process.event.entity;

/**
 * EventType
 * - 파일 처리 생명주기의 이벤트 유형을 정의하는 열거형
 * - FileEvent 엔티티에서 처리 상태 변화를 구분하는 데 사용
 *
 * @author 구본상
 * @since 2026-03-26
 */
public enum EventType {
    /** CDR 처리 대기 큐에 등록된 상태 */
    QUEUED,

    /** CDR 처리가 시작된 상태 */
    START,

    /** CDR 처리가 정상 완료된 상태 */
    DONE,

    /** CDR 처리가 실패한 상태 */
    FAIL
}
