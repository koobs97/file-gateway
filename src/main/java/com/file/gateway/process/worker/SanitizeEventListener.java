package com.file.gateway.process.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업로드 트랜잭션 커밋 후 CDR 처리를 트리거한다.
 * AFTER_COMMIT 보장: 워커가 DB에서 파일 메타데이터를 안전하게 조회할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanitizeEventListener {

    private final SanitizeWorker sanitizeWorker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFileUploaded(FileUploadedEvent event) {
        log.info("[CDR] 처리 큐 등록: fileId={}, fileSize={}bytes", event.fileId(), event.fileSize());
        sanitizeWorker.processAsync(event.fileId());
    }
}
