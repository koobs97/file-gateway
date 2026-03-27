package com.file.gateway.process.worker;

/**
 * FileUploadedEvent
 * - 파일 업로드 완료 시 Spring 애플리케이션 이벤트로 발행되는 불변 레코드
 * - SanitizeEventListener가 이 이벤트를 수신하여 CDR 처리를 트리거한다
 * - 트랜잭션 커밋 이후에 처리되므로 fileId로 DB 조회가 안전하게 보장된다
 *
 * @param fileId   업로드된 파일의 고유 ID
 * @param fileSize 업로드된 파일 크기 (바이트)
 * @author 구본상
 * @since 2026-03-26
 */
public record FileUploadedEvent(
        /** 업로드된 파일의 고유 ID */
        Long fileId,

        /** 업로드된 파일 크기 (바이트) */
        long fileSize
) {}
