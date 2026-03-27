package com.file.gateway.file.dto;

import com.file.gateway.process.event.entity.FileEvent;

import java.time.LocalDateTime;

/**
 * FileEventResponse
 * - 파일 이벤트 이력 조회 API 응답 DTO이다.
 * - 이벤트 ID, 이벤트 타입, 페이로드(JSON), 이벤트 발생 시각을 포함한다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record FileEventResponse(
        /** 이벤트 고유 식별자 */
        Long id,
        /** 이벤트 타입 (예: QUEUED, PROCESSING, COMPLETED, FAILED) */
        String eventType,
        /** 이벤트 관련 추가 데이터 (JSON 문자열) */
        String payload,
        /** 이벤트 발생 시각 */
        LocalDateTime createdAt
) {
    /**
     * FileEvent 엔티티로부터 FileEventResponse 인스턴스를 생성한다.
     *
     * @param event 변환할 FileEvent 엔티티
     * @return 생성된 FileEventResponse 인스턴스
     */
    public static FileEventResponse from(FileEvent event) {
        return new FileEventResponse(
                event.getId(),
                event.getEventType().name(),
                event.getPayload(),
                event.getCreatedAt()
        );
    }
}
