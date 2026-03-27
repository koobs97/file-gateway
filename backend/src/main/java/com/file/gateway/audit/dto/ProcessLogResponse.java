package com.file.gateway.audit.dto;

import com.file.gateway.process.event.entity.FileProcessLog;

import java.time.LocalDateTime;

/**
 * ProcessLogResponse
 * - 파일 처리 단계별 로그 조회 API의 응답 본문을 담는 DTO 레코드
 * - FileProcessLog 엔티티를 클라이언트 응답 형태로 변환
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record ProcessLogResponse(
        /** 처리 로그 고유 식별자 */
        Long id,
        /** 처리 로그가 속한 파일의 ID */
        Long fileId,
        /** 처리 단계 이름 (예: UPLOAD, ANALYZE, SANITIZE) */
        String step,
        /** 처리 결과 상태 (예: SUCCESS, FAILURE) */
        String status,
        /** 처리 결과에 대한 상세 메시지 */
        String message,
        /** 로그 생성일시 */
        LocalDateTime createdAt
) {
    /**
     * FileProcessLog 엔티티를 ProcessLogResponse로 변환
     *
     * @param log 변환할 파일 처리 로그 엔티티 (null 불허)
     * @return 파일 처리 로그 정보를 담은 ProcessLogResponse 인스턴스
     */
    public static ProcessLogResponse from(FileProcessLog log) {
        return new ProcessLogResponse(
                log.getId(),
                log.getFile().getId(),
                log.getStep().name(),
                log.getStatus().name(),
                log.getMessage(),
                log.getCreatedAt()
        );
    }
}
