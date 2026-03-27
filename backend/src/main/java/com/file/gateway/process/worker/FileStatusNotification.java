package com.file.gateway.process.worker;

import java.time.LocalDateTime;

/**
 * FileStatusNotification
 * - CDR 처리 결과를 WebSocket 구독자에게 실시간으로 전달하는 알림 페이로드 레코드
 * - /topic/files/{fileId} 채널로 전송되며 클라이언트가 파일 상태를 즉시 갱신할 수 있도록 한다
 *
 * @param fileId    처리 완료된 파일의 고유 ID
 * @param status    처리 결과 상태 문자열 (DONE, FAIL 등)
 * @param timestamp 알림 생성 일시
 * @author 구본상
 * @since 2026-03-26
 */
public record FileStatusNotification(
        /** 처리 완료된 파일의 고유 ID */
        Long fileId,

        /** 처리 결과 상태 문자열 (FileStatus enum 이름) */
        String status,

        /** 알림이 생성된 일시 */
        LocalDateTime timestamp
) {}
