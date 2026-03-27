package com.file.gateway.process.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * SanitizeEventListener
 * - 업로드 트랜잭션 커밋 후 CDR 처리를 트리거한다.
 * - AFTER_COMMIT 보장: 워커가 DB에서 파일 메타데이터를 안전하게 조회할 수 있다.
 * - Spring의 트랜잭션 이벤트 메커니즘을 사용하여 업로드 완료 후 비동기 처리를 시작
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanitizeEventListener {

    /** CDR 무해화 처리를 실행하는 워커 서비스 */
    private final SanitizeWorker sanitizeWorker;

    /**
     * 파일 업로드 트랜잭션이 성공적으로 커밋된 후 비동기 CDR 처리를 시작한다.
     * 트랜잭션 커밋 이후에 실행되므로 워커에서 파일 메타데이터를 안전하게 조회할 수 있다.
     *
     * @param event 업로드된 파일 정보를 담은 이벤트 (fileId, fileSize)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFileUploaded(FileUploadedEvent event) {
        log.info("[CDR] 처리 큐 등록: fileId={}, fileSize={}bytes", event.fileId(), event.fileSize());
        sanitizeWorker.processAsync(event.fileId());
    }
}
